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
6. **Deploy:** optional, only when `DEPLOY_HOST` is set - runs `ansible/site.yml`.

Promotion model for v1: **manual** (build parameters), i.e. continuous delivery. Enabling a
`when { branch 'main' }` deploy stage turns it into full continuous deployment.

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
- The Jenkins stage wiring and a real Ansible run against a server **were not executed here**
  (Jenkins/Ansible not installed, no remote server) - the playbooks are provided, YAML-parsed
  and statically reviewed; see `docs/20-final-validation.md`.
