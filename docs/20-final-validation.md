# 20. Final validation report

**Date:** 2026-10-19
**Machine:** Windows 11, Microsoft OpenJDK 21.0.12.1, Git 2.47.1, Docker Desktop (engine 29.6.2),
Google Chrome 155.0.8059.82 (ChromeDriver 155.0.8059.84 via Selenium Manager), Python 3.13.2.
**Not installed on this machine:** Maven (Maven Wrapper used instead), Jenkins, Ansible.

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
| `Jenkinsfile` | written to use only the commands verified above | **REVIEWED - NOT EXECUTED** (no Jenkins on this machine) |
| `ansible-playbook --syntax-check` / run | n/a | **NOT EXECUTED** (Ansible not installed; YAML parsed + reviewed instead) |

## 5. Documented-but-unverified items (honest list)

1. **Jenkins end-to-end run** - requires a Jenkins controller/agent (`docs/12-jenkins-setup.md`
   explains setup). All individual pipeline commands were executed locally.
2. **Ansible playbook against a real server** - requires a provisioned host and Ansible
   (`docs/18-ansible-provisioning.md`). Files are YAML-valid and statically reviewed.
3. **PostgreSQL profile** (`application-postgres.yml`) - driver included, config documented;
   not exercised on this machine (H2 is the verified path).
4. **Registry push / remote deployment / PR flow** - no remote configured for this repository
   by design; no remotes or URLs were invented.
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
- Git: repository initialised with a single meaningful initial commit; no remote configured.
