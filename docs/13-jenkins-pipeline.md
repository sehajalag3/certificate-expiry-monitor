# 13. Pipeline & deployment stages

Definition: [`Jenkinsfile`](../Jenkinsfile) (declarative pipeline).

## 13.1 Stage overview

```
Checkout -> Build and unit tests -> Selenium UI journeys -> Package -> Docker image -> Push -> Deploy
              (37 tests)              (5 journeys)          (jar)     (build)     (opt)   (opt)
```

| Stage | Command | Purpose | Gate |
|-------|---------|---------|------|
| Trigger | `pollSCM('H/5 * * * *')` | build on new commits without an inbound webhook | always registered |
| Checkout | `checkout scm`, `git rev-parse` | source at the right commit | always |
| Build and unit tests | `./mvnw -B clean test` | compile + 37 unit/integration tests | always; JUnit results published |
| Selenium UI journeys | `./mvnw -B test -Pui` | 5 headless Chrome journeys | `RUN_UI_TESTS` param; screenshots archived |
| Package | `./mvnw -B -DskipTests package` | executable jar; `test -f` asserts it exists | always |
| Docker image | `where docker`, `docker build -t <reg>/<img>:1.0.0-<no> -t <reg>/<img>:latest .` | reproducible runtime | `BUILD_IMAGE` param; early failure when the CLI is missing |
| Push image | `docker login/push` | publish for deployment | `PUSH_IMAGE` + credentials |
| Deploy | `ansible-playbook ... ansible/site.yml` | provision + start on the target host | `DEPLOY_HOST` param |

`post { always }` cleans the workspace; failures keep surefire XML and
`target/selenium-screenshots/**` for diagnosis.

## 13.2 Deployment flow (what happens on the server)

1. Ansible connects to `DEPLOY_HOST` (SSH key credential).
2. Roles `common` -> `docker` ensure packages, user and Docker Engine exist (idempotent).
3. Role `app` renders `app.env` and a systemd unit, then starts the container
   `certificate-monitor` publishing `8080` and mounting `/opt/certificate-expiry-monitor/data`.
4. Health gate: playbook polls `http://127.0.0.1:8080/actuator/health` (20 retries x 5 s)
   until it returns 200 - the deployment fails loudly otherwise.
5. On success the app is live; logs via `journalctl -u certificate-monitor` or
   `docker logs certificate-monitor`.

Details: `docs/17-continuous-deployment.md`, `docs/18-ansible-provisioning.md`.

## 13.3 Parameter reference

| Parameter | Default | Meaning |
|-----------|---------|---------|
| `RUN_UI_TESTS` | false | run the Selenium stage (needs Chrome on the agent) |
| `BUILD_IMAGE` | false | build the Docker image (needs the Docker CLI on the agent PATH) |
| `PUSH_IMAGE` | false | push to registry (needs `dockerhub-credentials`) |
| `REGISTRY` | `docker.io` | registry host |
| `IMAGE_NAME` | `sehaj0003/certificate-expiry-monitor` | repository |
| `DEPLOY_HOST` | empty | `user@host` for Ansible; empty = skip deploy |

## 13.4 Manual deployment (without Jenkins)

```bash
./mvnw -B -DskipTests package
docker build -t certificate-expiry-monitor:1.0.0 .
# on the target host
ansible-playbook -i ansible/inventory/hosts.ini ansible/site.yml -e deploy=true \
  -e image=certificate-expiry-monitor:1.0.0
```

Or plain Docker: `docker compose up -d --build` (see `docs/16-docker.md`).

## 13.5 Rollback

1. Redeploy the previous image tag: `ansible-playbook ... -e image=<reg>/app:<old-tag> -e deploy=true`
   (systemd re-runs `docker run` with the new tag; data volume is untouched).
2. For compose-based hosts: `docker compose down && git checkout <tag> && docker compose up -d --build`.
3. Database changes in v1 are additive only (`ddl-auto: update`), so rolling the jar/image back
   is safe.

## 13.6 Verification status

Locally re-executed (see final validation doc): `clean test` (37), `test -Pui` (5), `package`,
`docker build`, `docker compose config`, container health check.

Executed on the local Jenkins 2.580.1 controller (Windows, `LocalSystem`):

| Build | Stages exercised | Evidence |
|-------|------------------|----------|
| #1 | checkout, 37 tests, package | `BUILD SUCCESS`, JAR archived |
| #2 | + Selenium UI journeys | `Tests run: 5`, JUnit results published, JAR archived |
| #3 | + Docker image | `Successfully tagged …:1.0.0-3` and `…:latest`, JAR archived |
| #4 | manual `Build with Parameters` | `BUILD SUCCESS`; Push/Deploy skipped, so `pollSCM` registered and the job now saves an `SCMTrigger` |
| #5, #6 | + Push image | `docker login … unauthorized: incorrect username or password` - see the root cause below |

Green UI runs write no screenshots; the pipeline checks that `target/selenium-screenshots`
exists before archiving, which removes the "No artifacts found ... Configuration error?"
warning seen in build #2.

### Push credential handling (root cause of builds #5 / #6)

The original stage ran, under `bat` (cmd.exe):

```bat
echo %DOCKER_PASS% | docker login "%REGISTRY%" -u "%DOCKER_USER%" --password-stdin
```

On Windows, cmd.exe inserts a **trailing space** before the pipe, so the token on stdin is
`<token> \r\n` (verified locally: `abc123` arrives as `abc123 \r\n`). `docker login
--password-stdin` strips only terminal CR/LF/CR and treats the rest as opaque
(`cli/command/registry/login.go`, `readSecretFromStdin`), so the space becomes part of the
token and Docker Hub answers `unauthorized: incorrect username or password`. The credential
itself is correct (username `sehaj0003`, one GLOBAL entry), which is why regenerating the
token did not help.

The stage now pipes the environment variable from PowerShell instead, without ever
interpolating the secret into the command line or logs:

```bat
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = $env:DOCKER_PASS; $p | docker login $env:REGISTRY --username $env:DOCKER_USER --password-stdin; exit $LASTEXITCODE"
```

The same invocation prints a DIAG line that distinguishes a **registry rejection**
(`unauthorized …`, exit code from `docker login`) from a pipeline/parsing problem (an empty
variable, a PowerShell/cmd error, or `'docker' is not recognized`), and warns (without
printing the value) when the stored token has surrounding whitespace.

Ansible was installed in WSL (Ubuntu 26.04, ansible-core 2.20.1):
`ansible-playbook --syntax-check ansible/site.yml` passed and a `--check` run of the
`common` + `app` roles against `localhost` finished with `failed=0`.
