# 3. Software requirements specification (SRS)

**Project:** Certificate Expiry Monitor
**Version:** 1.0.0 | **Status:** implemented and verified (see `docs/20-final-validation.md`)

## 1. Purpose

A self-hosted web application that maintains an inventory of digital certificates, computes
each certificate's remaining lifetime, and highlights expired and soon-to-expire certificates
so renewals happen before they cause outages.

## 2. Scope

Included: certificate CRUD, validation, search/filter/sort/pagination, dashboard and
exceptions views, demo dataset, configurable warning threshold, health endpoint, tests
(unit, integration, browser), container build, CI/CD pipeline, provisioning playbooks.

Excluded: certificate discovery/scanning, private key handling, CA integrations, automatic
renewal, authentication, alert delivery channels.

## 3. Users

- **Operations / platform engineers** - watch the dashboard, renew certificates, keep owners honest.
- **QA / security reviewers** - audit inventory, check expiry across environments.
- **Administrators** - configure threshold, seed/reset demo data, deploy the app.

## 4. Functional requirements

| ID | Requirement | Implemented in |
|----|-------------|----------------|
| FR1 | The system shall classify each certificate as EXPIRED, EXPIRING_SOON or VALID from its expiry date, today's date and the warning threshold | `ExpiryStatusCalculator` |
| FR2 | The threshold shall default to 30 days and be configurable (`app.warning-threshold-days`), including 0 and rejecting negatives | `AppProperties` (+ `@Min(0)` validation) |
| FR3 | The system shall show a dashboard with total/expired/expiring-soon/valid counts and a list of expiry alerts | `DashboardController`, `dashboard.html` |
| FR4 | The system shall list certificates with free-text search over name, hostname, issuer, owner and notes | `CertificateService.find...` (JPA Specification) |
| FR5 | The system shall filter the list by status, certificate type and environment, combined with search | `CertificateController.list` |
| FR6 | The system shall support sorting by name, hostname, issuer, owner, type, environment, issue date, expiry date, created and updated, ascending/descending, with an allow-list of sortable fields | `SORTABLE_FIELDS` in `CertificateController` |
| FR7 | The system shall paginate the list (page, size in {5,10,20,50}) | `CertificateController.list` |
| FR8 | The system shall create a certificate after validating required fields, lengths, hostname pattern, expiry-after-issue and issue-date-not-in-future | `CertificateForm` + `CertificateService.create` |
| FR9 | The system shall edit an existing certificate with the same validation | `POST /certificates/{id}` |
| FR10 | The system shall require an explicit confirmation step before deleting, then delete and report success | `delete-confirm.html`, `POST /certificates/{id}/delete` |
| FR11 | The system shall show a detail page with all fields, computed status, days remaining and audit timestamps | `certificates/detail.html` |
| FR12 | The system shall show an exceptions view containing expired and expiring-soon certificates | `/exceptions` |
| FR13 | The system shall seed a demo dataset of 10 certificates on first start when demo mode is enabled, and reset it on demand (`POST /demo/reset`) | `DemoDataService`, `DemoDataInitializer` |
| FR14 | The system shall render user-friendly error pages for not-found (404) and unexpected (500) errors | `GlobalExceptionHandler`, `error.html` |
| FR15 | The system shall expose a health endpoint suitable for container and pipeline probes | Spring Actuator `/actuator/health` |

## 5. Non-functional requirements

| ID | Requirement | How it is met |
|----|-------------|---------------|
| NFR1 | Starts locally with no external services | H2 file database default, vendored Bootstrap, Maven wrapper |
| NFR2 | Full build and test without a browser | `ui` profile excluded from the default surefire run |
| NFR3 | Portability across developer machines | wrapper, env-var configuration, Docker image, no absolute paths |
| NFR4 | Deterministic, machine-independent date handling | `Clock` bean injected into calculator/service; ISO form dates |
| NFR5 | Data survives restarts | H2 file (`./data`) / Docker named volume / PostgreSQL profile |
| NFR6 | Graceful shutdown | `server.shutdown: graceful` |
| NFR7 | Observability | structured console logging, health endpoint, `info.app.*` metadata |
| NFR8 | Security baseline | non-root container user, no secrets in repo, validation on every write path, error details hidden (`include-message: never`) |
| NFR9 | Testability | 37 automated tests + 5 Selenium journeys, all commands documented |
| NFR10 | Automatability | every step available as a CLI command used by `Jenkinsfile` |

## 6. External interfaces

- **Web:** HTTP/1.1 on `SERVER_PORT` (default 8080), server-rendered HTML, JSON health endpoint.
- **Database:** JDBC (H2 default; PostgreSQL via `spring.profiles.active=postgres`).
- **CI/CD:** Jenkins (pipeline definition in `Jenkinsfile`), Docker daemon, Ansible >= 2.14 target.

## 7. Constraints

- Java 21 and Spring Boot 3.5.x; no other framework rewrites.
- v1 ships without authentication - deploy behind a reverse proxy/VPN if exposed.
- Browser tests require Google Chrome on the agent.

## 8. Assumptions

- Expiry dates are maintained manually (no auto-discovery in v1).
- Dates are calendar dates (no time zones); "today" is the server's local date.
- Demo mode is acceptable for evaluation and disabled in production via `DEMO_DATA_ENABLED=false`.
