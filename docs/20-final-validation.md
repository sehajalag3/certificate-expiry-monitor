# 20. Final validation report

**Date:** 2026-10-19 (application validation), 2026-10-20 (CI/CD addendum, section 8)
**Machine:** Windows 11, Microsoft OpenJDK 21.0.12.1, Git 2.47.1, Docker Desktop (engine 29.6.2),
Google Chrome 155.0.8059.82 (ChromeDriver 155.0.8059.84 via Selenium Manager), Python 3.13.2,
Jenkins 2.580.1 (local Windows service); Ansible-core 2.20.1 inside WSL Ubuntu 26.04.
**Not installed on this machine:** Maven (Maven Wrapper used instead), native Ansible on Windows.

Every row marked **EXECUTED** below was run in this repository and its result captured.

## 1. Build & test

| # | Command | Expected | Result | Status |
|---|---------|----------|--------|--------|
| 1 | `./mvnw -B test` | 37 tests pass | `Tests run: 37, Failures: 0, Errors: 0, Skipped: 0` + `BUILD SUCCESS` | **EXECUTED - PASS** |
| 2 | `./mvnw -B test -Pui` | 5 Selenium journeys pass (headless Chrome) | `Tests run: 5, Failures: 0, Errors: 0` + `BUILD SUCCESS` (20.2 s) | **EXECUTED - PASS** |
| 3 | `./mvnw -B -DskipTests package` | executable jar | `Building jar: target/certificate-expiry-monitor-1.0.0.jar` + `BUILD SUCCESS` | **EXECUTED - PASS** |

Test breakdown (default run): 7 calculator + 10 service + 5 repository + 12 MockMvc + 3 demo-data.

## 2. Application smoke (jar on `http://localhost:8081`)

| Check | Result | Status |
|-------|--------|--------|
| `GET /actuator/health` | `{"status":"UP","groups":["liveness","readiness"]}` | **EXECUTED - PASS** |
| `GET /`, `/exceptions`, `/certificates`, `/certificates/new`, `/certificates/1`, `/certificates/1/edit`, `/certificates/1/delete` | all `200` | **EXECUTED - PASS** |
| `GET /certificates?status=EXPIRED&sort=expiryDate&direction=asc` | `200` | **EXECUTED - PASS** |
| `GET /certificates?q=vpn&type=TLS_SSL&environment=PRODUCTION&page=0&size=5` | `200` | **EXECUTED - PASS** |
| Static asset `/vendor/bootstrap/css/bootstrap.min.css` | `200` (vendored, no CDN) | **EXECUTED - PASS** |
| `POST /certificates` (valid form) | redirects to detail, page contains `was created` | **EXECUTED - PASS** |
| `POST /certificates/1/delete` | redirects to list, page contains `was deleted` | **EXECUTED - PASS** |
| `GET /import` (unknown path) | custom 404 error page, footer shows `Warning threshold: 30 days` | **EXECUTED - PASS** |
| `GET /certificates/99999` (missing id) | custom 404 error page | **EXECUTED - PASS** |
| Dashboard counts for demo data | cards `10 / 2 / 4 / 4` asserted by MockMvc **and** Selenium | **EXECUTED - PASS** (in tests) |

## 3. Containerisation

| Check | Result | Status |
|-------|--------|--------|
| `docker build -t certificate-expiry-monitor:1.0.0 .` | image built (Maven build inside container) | **EXECUTED - PASS** |
| `docker compose config -q` | valid configuration | **EXECUTED - PASS** |
| `docker run -d -p 8081:8080 ...` | `Started CertificateExpiryMonitorApplication in 6.533 s` | **EXECUTED - PASS** |
| Container `HEALTHCHECK` | state **healthy** | **EXECUTED - PASS** |
| `GET /actuator/health` through published port | `{"status":"UP",...}` | **EXECUTED - PASS** |
| Dashboard through container | `HTTP 200`, title `Dashboard \| Certificate Expiry Monitor` | **EXECUTED - PASS** |

## 4. Static validation of infrastructure files

| Check | Result | Status |
|-------|--------|--------|
| YAML parse of `docker-compose.yml`, `ansible/site.yml`, all Ansible role files, `application*.yml` | `10/10 files parsed OK` (Python `yaml.safe_load`) | **EXECUTED - PASS** |
| PowerShell syntax of the 3 `scripts/*.ps1` | `ParseFile` - 0 errors | **EXECUTED - PASS** |
| `bash -n` on the 3 `scripts/*.sh` | exit 0 for all | **EXECUTED - PASS** |
| `Jenkinsfile` | written to use only the commands verified above | **EXECUTED in Jenkins build #1** (tests + package) - see section 8 |
| `ansible-playbook --syntax-check` / run | valid playbook | **EXECUTED in WSL** (`--syntax-check` passed; `--check` run of `common` + `app`: `failed=0`) - see section 8 |

## 5. Documented-but-unverified items (honest list)

1. **Jenkins end-to-end run** - build #1 covered checkout, unit/integration tests and packaging;
   the Selenium, Docker image, push and deploy stages have not yet run inside Jenkins because
   their parameters default to `false`. See section 8.
2. **Ansible playbook against a real server** - requires a provisioned host and Ansible
   (`docs/18-ansible-provisioning.md`). Syntax check and a localhost check-mode run passed;
   no remote host exists yet.
3. **PostgreSQL profile** (`application-postgres.yml`) - driver included, config documented;
   not exercised on this machine (H2 is the verified path).
4. **Registry push / remote deployment / PR flow** - a public GitHub remote exists, but no
   image has been pushed to a registry and no host has been provisioned; the Jenkins
   credential store is empty.
5. **Screenshots for documentation** - failure screenshots exist only under
   `target/selenium-screenshots/` (test artifacts); no fabricated UI screenshots are included.

## 6. Defects found and fixed during validation

| Defect | Fix |
|--------|-----|
| Edit form submitted empty dates (locale-dependent date rendering, `19/10/25` in `<input type="date">`) so saves failed validation | fixed `@DateTimeFormat(pattern = "yyyy-MM-dd")` on `CertificateForm` (root cause proven with a real HTTP fetch of the edit page) |
| UI reset call followed the `302` to `/;jsessionid=...` and lost the flash / hit 500 | test client now uses `Redirects.DONT_FOLLOW` for `POST /demo/reset` |
| Stale element references while the table re-rendered after search | `rowFor(name)` helper retries on `StaleElementReferenceException`; flash asserted after a content anchor |
| Unknown paths returned 500 instead of 404 | `NoResourceFoundException` handled as 404 in `GlobalExceptionHandler` |
| Error page footer rendered `threshold: null` | error views now receive `warningThresholdDays` + `demoEnabled` |
| Negative threshold accepted silently in config | `@Validated` + `@Min(0)` on `AppProperties`, calculator rejects negative at use |

## 7. Final state

- Source: 25 main classes, 9 test classes, 10 templates, vendored Bootstrap.
- Tests: **42 automated (37 + 5), all passing**, runnable by anyone with JDK 21 (+ Chrome for UI).
- Artifacts: `target/certificate-expiry-monitor-1.0.0.jar`, Docker image
  `certificate-expiry-monitor:1.0.0`.
- Docs: 21 documents in `docs/` + README + CONTRIBUTING.
- Git: `main` at `c80bff0` (Windows Jenkins fix on top of the initial import), pushed to
  `https://github.com/sehajalag3/certificate-expiry-monitor.git`.

## 8. CI/CD addendum - 2026-10-20

| Check | Result | Status |
|-------|--------|--------|
| Jenkins controller | 2.580.1 at `http://localhost:8080`, Windows service running as `LocalSystem`, job `Certificate-Expiry-Monitor` (Pipeline script from SCM, `*/main`) | **EXECUTED - PASS** |
| `.\mvnw.cmd -B test` re-run | `Tests run: 37, Failures: 0, Errors: 0` | **EXECUTED - PASS** |
| `.\mvnw.cmd -B test -Pui` re-run | `Tests run: 5, Failures: 0, Errors: 0` | **EXECUTED - PASS** |
| Jenkins build #1 (checkout, test, package) | console log shows `Tests run: 37`, `BUILD SUCCESS`, jar archived | **EXECUTED - PASS** |
| Jenkins build #2 (+ Selenium) | `Tests run: 5, Failures: 0`; `junitResult.xml` published; jar archived | **EXECUTED - PASS** |
| Jenkins build #3 (+ Docker) | `Successfully tagged sehaj0003/certificate-expiry-monitor:1.0.0-3` and `:latest`; jar archived | **EXECUTED - PASS** |
| Empty screenshot archive warning | build #2/#3 logged "No artifacts found ... Configuration error?"; archive step now guarded by `fileExists(...)` | **FIXED** |
| Jenkins credentials | `dockerhub-credentials` present (GLOBAL, username `sehaj0003`, single entry, access token); `deploy-ssh-key` still missing | **PARTIAL** |
| GitHub trigger | job stores an `SCMTrigger` (`scm-polling.log` polls `H/5 * * * *`); builds #7 and #8 were triggered automatically by pushed commits | **REGISTERED - PASS** |
| Jenkins builds #4 | manual `Build with Parameters`: `BUILD SUCCESS`, Push/Deploy skipped | **EXECUTED - PASS** |
| Jenkins builds #5 / #6 (push) | `docker login` failed: `unauthorized: incorrect username or password` even after a fresh token | **FAIL - root-caused** |
| Push credential handling | cmd.exe `echo %DOCKER_PASS% |` appends a trailing space that `docker login --password-stdin` keeps; replaced with a PowerShell `$env:DOCKER_PASS | docker login ...` pipe | **FIXED** |
| Auth diagnostic | exact new command run locally with dummy credentials reached Docker Hub and returned `unauthorized` + exit 1, clearly distinct from a syntax/parsing error | **EXECUTED - PASS** |
| Jenkins build #7 (SCM-triggered) | `BUILD SUCCESS`, 37 tests, new Jenkinsfile parsed; image/push skipped on the stale parameter defaults | **EXECUTED - PASS** |
| Jenkins build #8 (SCM-triggered) | `Login Succeeded`, `DIAG: docker login succeeded`, `1.0.0-8` + `latest` pushed, `docker logout` ran, `BUILD SUCCESS`; Docker Hub API lists `1.0.0-8` and `latest` (`sha256:ed267dca…`) | **EXECUTED - PASS** |
| Ansible validation | `--syntax-check site.yml` OK; `--check` on `common` + `app` roles: `failed=0`; docker role fails in check mode because its apt repository is only simulated | **EXECUTED (partial)** |
| Local deployment/recovery rehearsal | Jenkins image `1.0.0-3` on port 8082 + separate volume: health `UP`, created certificate survived restart, tag swap to `1.0.0` preserved data; container/volume removed | **EXECUTED - PASS** |
| Remote deployment / rollback | no target host; inventory contains the placeholder `app.example.com` only | **NOT EXECUTED** |
| Docker Hub push | verified end-to-end by build #8 via the registered SCM trigger; `sehaj0003/certificate-expiry-monitor:1.0.0-8` and `:latest` are live on Docker Hub | **EXECUTED - PASS** |

## 9. Final task checklist - 2026-10-20 (second session)

Every row below was executed in this repository, and the evidence column names the artefact that
can be re-checked. **Nothing here is a remote deployment** - the only Linux node used is the local
WSL distro, and the production inventory still points at the placeholder host.

| # | Task | Evidence | Status |
|---|------|----------|--------|
| 1 | GitHub CLI authenticated | `gh auth status` -> `Logged in to github.com account sehajalag3`, scopes `gist, read:org, repo, workflow` | **EXECUTED - PASS** |
| 2 | Push a branch that must fail CI | branch `test/deliberate-failure` at `f198541` pushed; `git ls-remote --heads origin` lists it next to `main` | **EXECUTED - PASS** |
| 3 | Prove the failure is real | `.\mvnw.cmd -B test "-Dtest=DeliberateFailureDrillTest"` -> `Tests run: 1, Failures: 1, Errors: 0`, `BUILD FAILURE`, exit code 1 | **EXECUTED - PASS** |
| 4 | Jenkins job `Certificate-Expiry-Monitor-FailureDrill` on `test/deliberate-failure` | job exists under `C:\ProgramData\Jenkins\.jenkins\jobs\`; its `config.xml` shows `CpsScmFlowDefinition` -> Git -> `*/test/deliberate-failure`, `scriptPath=Jenkinsfile`, `<disabled>false` | **EXECUTED - job verified** |
| 5 | First run of that job | `builds/` is empty and the job declares no `<triggers/>`, so it waits for a manual **Build**; Jenkins denies anonymous reads (HTTP 403) and no credential was supplied in this session | **PENDING - manual trigger needed** |
| 6 | Ansible root execution in WSL, no `NOPASSWD` | `wsl -d Ubuntu -u root -- id` -> `uid=0(root) gid=0(root)`; no sudoers file was created or edited | **EXECUTED - PASS** |
| 7 | Isolated test environment | new `ansible/inventory/wsl-test.ini` (`localhost`, `ansible_connection=local`) + `ansible/inventory/wsl-test-vars.yml` (`app_home=/opt/certmon-wsl-drill`, `app_port=8090`); default inventory and role defaults untouched | **EXECUTED - PASS** |
| 8 | Playbook: syntax check | `ansible-playbook -i inventory/wsl-test.ini site.yml --syntax-check` -> `playbook: site.yml`, exit 0 | **EXECUTED - PASS** |
| 9 | Playbook run 1 - provisioning | recap `ok=16 changed=11 failed=0 skipped=2` (packages, Docker CE, app user, dirs, unit) | **EXECUTED - PASS** |
| 10 | Playbook run 2 - first deploy | recap `ok=16 changed=1 failed=1`; health gate failed with `[Errno 104] Connection reset by peer` on `http://127.0.0.1:8090/actuator/health` -> defect 1 found | **EXECUTED - FAIL (defect found)** |
| 11 | Playbook run 3 - deploy after the fix | recap `ok=17 changed=2 failed=0`; health `{"status":"UP","groups":["liveness","readiness"]}` | **EXECUTED - PASS** |
| 12 | Playbook run 4 - same command again | recap `ok=17 changed=0 failed=0` -> **idempotent** | **EXECUTED - PASS** |
| 13 | Application health on the WSL node | `/actuator/health` UP, `/` and `/certificates` HTTP 200 with **10 certificate rows**, container `Up (healthy) 0.0.0.0:8090->8080/tcp` | **EXECUTED - PASS** |
| 14 | Rollback without deleting data | unit disabled + unit file removed + `docker rm -f`; then `PASS: app data marker preserved`, `PASS: /root canary preserved`, `PASS: /opt canary preserved`, `PASS: unrelated app data preserved`, port closed | **EXECUTED - PASS** |
| 15 | Recovery with the same playbook | run 5 recap `ok=18 changed=2 failed=0`; health `UP`, still **10 rows**, marker md5 `854384fb431220a85a7aed5f8f7aafdc` unchanged -> rollback removed deployment objects, never data | **EXECUTED - PASS** |
| 16 | Ansible defects found and fixed | 3 fixes (port mapping `app_container_port`, removed `yaml` callback, `ansible_facts` deprecation) - detail in [`18-ansible-provisioning.md`](18-ansible-provisioning.md) 18.6; validated by runs 3/4/5 | **FIXED - PASS** |
| 17 | GitHub pull request + code review | PR **#1** `docs/final-evidence-and-ansible-drill` -> `main`, opened with the PR template, diff reviewed against the [`11-collaboration.md`](11-collaboration.md) checklist, `gh pr review --approve` | **EXECUTED - PASS** |
| 18 | Merge without breaking `main` | `gh pr merge --merge` (merge commit, matching `main` history style) | **EXECUTED - PASS** |
| 19 | `main` green after the merge | `.\mvnw.cmd -B test` re-run on `main` -> `Tests run: 37, Failures: 0, Errors: 0`, `BUILD SUCCESS` | **EXECUTED - PASS** |
| 20 | Documentation of the drill | [`18-ansible-provisioning.md`](18-ansible-provisioning.md) 18.6 (runs, defects, limits), [`22-collaboration-log.md`](22-collaboration-log.md) (branch + PR log), this section | **EXECUTED - PASS** |

### Still not verified (honest list, updated)

1. **No remote host.** `ansible/inventory/hosts.ini` still holds the placeholder `app.example.com`;
   all five playbook runs used the isolated local WSL inventory. This session does **not** claim a
   remote deployment - only WSL was tested.
2. **Jenkins failure-drill build.** The job is created and correctly pointed at
   `test/deliberate-failure`, but it has never run: Jenkins has no anonymous read (HTTP 403 on
   `/api/json`), the job declares no trigger, and no credential was available to this session.
   Everything else about the drill is done (branch pushed, failure proven locally: exit code 1).
3. **Windows -> WSL access.** `localhost:8090` and the WSL address `172.19.115.211:8090` are not
   reachable from Windows (`TcpTestSucceeded = False`, WSL NAT + Hyper-V firewall), so all health
   checks were taken from inside WSL.
4. **WSL lifecycle.** WSL tears down the distro's userspace between command sessions (observed:
   PID 1 start time equals the start of the next `wsl` command), so the deployed container only
   runs while a session is active; the unit is enabled and starts again at the next session.
5. PostgreSQL profile, image registry beyond Docker Hub, and a provisioned server remain
   unverified exactly as in section 5.
