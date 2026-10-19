# 13. Pipeline & deployment stages

Definition: [`Jenkinsfile`](../Jenkinsfile) (declarative pipeline).

## 13.1 Stage overview

```
Checkout -> Build and unit tests -> Selenium UI journeys -> Package -> Docker image -> Push -> Deploy
              (37 tests)              (5 journeys)          (jar)     (build)     (opt)   (opt)
```

| Stage | Command | Purpose | Gate |
|-------|---------|---------|------|
| Checkout | `checkout scm`, `git rev-parse` | source at the right commit | always |
| Build and unit tests | `./mvnw -B clean test` | compile + 37 unit/integration tests | always; JUnit results published |
| Selenium UI journeys | `./mvnw -B test -Pui` | 5 headless Chrome journeys | `RUN_UI_TESTS` param; screenshots archived |
| Package | `./mvnw -B -DskipTests package` | executable jar; `test -f` asserts it exists | always |
| Docker image | `docker build -t <reg>/<img>:1.0.0-<no> -t <reg>/<img>:latest .` | reproducible runtime | `BUILD_IMAGE` param |
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
| `RUN_UI_TESTS` | true | run the Selenium stage |
| `BUILD_IMAGE` | true | build the Docker image |
| `PUSH_IMAGE` | false | push to registry (needs `dockerhub-credentials`) |
| `REGISTRY` | `docker.io` | registry host |
| `IMAGE_NAME` | `your-dockerhub-user/certificate-expiry-monitor` | repository |
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

Locally re-executed (see final validation doc): `clean test`, `test -Pui`, `package`,
`docker build`, `docker compose config`, container health check.
Not executable here (no Jenkins): stage orchestration, credential binding, Ansible stage -
the playbook commands were validated statically and `ansible-playbook --syntax-check` is
unavailable because Ansible is not installed on this machine.
