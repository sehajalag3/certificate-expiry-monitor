# 17. Continuous deployment

## 17.1 Target state

```
git push  ->  Jenkins (test -> ui test -> package -> image)  ->  registry  ->  Ansible  ->  running container
                                                                                    ^
                                                                            health gate 200 OK
```

Every green build of `main` produces an immutable image tag `1.0.0-<build number>`; a
deployment moves a host to a specific tag. Rollback = deploy an older tag.

## 17.2 Pipeline gates (from `Jenkinsfile`)

1. **Quality:** `./mvnw -B clean test` must pass (37 tests) - JUnit results published.
2. **UI:** `./mvnw -B test -Pui` (toggle `RUN_UI_TESTS`) - screenshots archived on failure.
3. **Artifact:** jar existence asserted after packaging.
4. **Image:** `docker build` with build-number and `latest` tags.
5. **Publish:** optional, only when `PUSH_IMAGE=true` and credentials exist.
6. **Deploy Container:** optional, only when `BUILD_IMAGE=true` and `DEPLOY_CONTAINER=true` -
   `docker compose -f deploy/docker-compose.deploy.yml up -d --force-recreate` (fresh container
   from the just-built image, fronted by Nginx) and a health gate through the proxy on
   `DEPLOY_PORT` (default 8888).
7. **Deploy with Ansible:** optional, only when `DEPLOY_HOST` is set - runs `ansible/site.yml`
   (remote Linux host).

Promotion model for v1: **manual** (build parameters), i.e. continuous delivery. Enabling
`DEPLOY_CONTAINER` on the `main` build turns the container path into full continuous
deployment; setting `DEPLOY_HOST` enables the remote (Ansible) path.

## 17.3 Deployment mechanics

`ansible-playbook -i ansible/inventory/hosts.ini ansible/site.yml -e deploy=true -e image=<tag>`

Roles run in order (`ansible/site.yml`):

1. **common** - packages, `certmonitor` system user, `/opt/certificate-expiry-monitor/{data,logs}`
2. **docker** - Docker CE + compose plugin, service enabled, app user in `docker` group
3. **app** - renders `app.env` + systemd unit, starts `certificate-monitor` container,
   polls `/actuator/health` until 200 (retries 20 x 5 s)

Environment values come from role defaults (`ansible/roles/app/defaults/main.yml`) and can be
overridden per run (`-e warning_threshold_days=14`, `-e demo_data_enabled=false`).

## 17.4 Release checklist (v1)

- [ ] `main` green in Jenkins (unit + UI stages)
- [ ] Version bumped in `pom.xml` / `info.app.version` if cutting a release
- [ ] Image pushed with the release tag
- [ ] `DEMO_DATA_ENABLED=false` and `THYMELEAF_CACHE=true` for production runs
- [ ] Backup of the app data volume before the first deploy
- [ ] Post-deploy check: `/actuator/health` = UP, dashboard loads, a known certificate visible
- [ ] Previous image tag recorded in the release notes for rollback

## 17.5 Rollback

```bash
# systemd/Ansible hosts
ansible-playbook -i ansible/inventory/hosts.ini ansible/site.yml \
  -e deploy=true -e image=<registry>/certificate-expiry-monitor:1.0.0-<previous build>
systemctl status certificate-monitor
```

Data is stored in a separate volume (`/app/data`), so image swaps never touch the database.
Schema changes in v1 are additive (`ddl-auto: update`), keeping rollbacks safe.

## 17.6 Environment promotion

| Environment | How | Data | Demo mode |
|-------------|-----|------|-----------|
| Dev | `./mvnw spring-boot:run` | local `./data` H2 | on |
| CI | Jenkins stages, in-memory H2 | throwaway | on |
| Test/prod | Ansible + Docker image | volume or PostgreSQL | off |

## 17.7 Verification status

- Commands used by the deploy stage (`package`, `docker build`, health checks) **were executed
  and verified** on this machine.
- Jenkins builds #1-#3 are green; build #3 produced the deployable tag `1.0.0-3` (+ `latest`).
- The playbooks were syntax-checked (`ansible-playbook --syntax-check ansible/site.yml`) and
  the `common` + `app` roles ran in `--check` mode against localhost under WSL
  (Ubuntu 26.04, ansible-core 2.20.1) with `failed=0`.
- **Deployment/recovery rehearsal (2026-10-20, local Docker):** the Jenkins-built image was
  started on port 8082 with a separate volume, `/actuator/health` returned `UP`, a certificate
  created through the UI survived a container restart, and swapping to the older `1.0.0` tag on
  the same volume preserved the data (the documented rollback path). The disposable container
  and volume were removed afterwards.
- A **real deployment to a remote server** has not been performed: the inventory contains only
  the placeholder `app.example.com`.

## 17.8 Container deployment behind Nginx (verified, this machine)

The "server" for the container path is an Nginx reverse proxy in front of the app container,
described by `deploy/docker-compose.deploy.yml` + `deploy/nginx/nginx.conf`.

```
Browser -> Nginx (host :DEPLOY_PORT) -> certificate-monitor container (:8080, not published) -> H2 volume
```

Verified end to end on 2026-10-20:

| Check | Command | Result |
|-------|---------|--------|
| Compose validity | `docker compose -f deploy/docker-compose.deploy.yml config -q` | valid |
| Start fresh container + proxy | `IMAGE_REF=sehaj0003/certificate-expiry-monitor:1.0.0-8 HTTP_PORT=8888 docker compose -f deploy/docker-compose.deploy.yml up -d` | app container `healthy`, Nginx `Up 0.0.0.0:8888->80` |
| Health through proxy | `GET http://localhost:8888/actuator/health` | `{"status":"UP","groups":["liveness","readiness"]}` |
| Proxy self-check | `GET http://localhost:8888/nginx-health` | `ok` |
| Dashboard through proxy | `GET http://localhost:8888/` | `200`, title `Dashboard \| Certificate Expiry Monitor` |
| Visual evidence | headless Chrome screenshot | `docs/screenshots/deployed-dashboard.png` |

To redeploy/rollback, change `IMAGE_REF` to another immutable tag (e.g. `1.0.0-3`) and re-run
`up -d --force-recreate`; the named volume `deploy_certificate-data-deploy` keeps the data.
