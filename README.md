# Certificate Expiry Monitor

A Spring Boot web application that tracks TLS/SSL, email, code-signing and domain
certificates and shows which of them are **expired**, **expiring soon** or **valid**,
so nobody is surprised by an outage caused by a forgotten renewal.

![Java](https://img.shields.io/badge/Java-21-e76f00) ![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-6db33f) ![Build](https://img.shields.io/badge/tests-42%20passing-brightgreen)

## Features

- **Dashboard** with summary cards (total / expired / expiring soon / valid) and an expiry-alerts list
- **Certificate list** with search (`q`), filters (status, type, environment), sorting and pagination
- **Create / edit / delete** certificates with server-side validation (required fields, hostname
  pattern incl. `*.example.com`, expiry-after-issue, issue-date-not-in-future) and a delete
  confirmation screen
- **Certificate detail page** with days remaining, status badge and audit timestamps
- **Exceptions view** for expired and soon-to-expire certificates
- **Configurable warning threshold** (`app.warning-threshold-days`, default 30 days)
- **Demo dataset** of 10 certificates (2 expired, 4 expiring soon, 4 valid) seeded at startup
  and resettable from the UI/API (`POST /demo/reset`)
- **Health endpoint** (`/actuator/health`) for probes and pipelines

### Status rule

```
daysRemaining = expiryDate - today
daysRemaining < 0            -> EXPIRED
0 <= daysRemaining <= 30     -> EXPIRING_SOON   (30 = warning threshold, inclusive)
daysRemaining > 30           -> VALID
```

## Tech stack

| Layer     | Choice                                                        |
|-----------|---------------------------------------------------------------|
| Language  | Java 21                                                       |
| Framework | Spring Boot 3.5.5 (Web, Thymeleaf, Data JPA, Validation, Actuator) |
| Database  | H2 file database by default, PostgreSQL via `postgres` profile |
| Frontend  | Thymeleaf templates, vendored Bootstrap 5.3.3 (no CDN)        |
| Tests     | JUnit 5 + AssertJ + Mockito + MockMvc, Selenium 4.27 (`ui` profile) |
| Build     | Maven Wrapper (3.9.9) - no local Maven install needed          |
| Runtime   | Docker image, docker-compose, systemd/Ansible deployment       |
| CI/CD     | Jenkins declarative pipeline (`Jenkinsfile`)                  |

## Quick start

### Option 1 - run locally (no Maven install needed)

```powershell
# Windows
.\mvnw.cmd -B spring-boot:run
```

```bash
# Linux/macOS
./mvnw -B spring-boot:run
```

Open http://localhost:8080. The demo dataset is seeded automatically.

### Option 2 - Docker Compose

```bash
docker compose up -d --build
# or on Windows
.\scripts\deploy-docker.ps1
```

Open http://localhost:8080; data is persisted in the `certificate-data` volume.

### Option 3 - executable JAR

```bash
./mvnw -B -DskipTests package
java -jar target/certificate-expiry-monitor-1.0.0.jar
```

## Tests

```powershell
.\mvnw.cmd -B test        # 37 unit + integration tests (no browser required)
.\mvnw.cmd -B test -Pui   # + 5 Selenium journeys (Google Chrome required, headless by default)
```

Selenium failure screenshots and page dumps land in `target/selenium-screenshots/`.
Show a real browser with `-Dui.headless=false`.

## HTTP routes

| Method | Path                       | Purpose                                  |
|--------|----------------------------|------------------------------------------|
| GET    | `/`                        | Dashboard with counts and alerts         |
| GET    | `/exceptions`              | Expired / expiring-soon view             |
| GET    | `/certificates`            | List: `q`, `status`, `type`, `environment`, `page`, `size`, `sort`, `direction` |
| GET    | `/certificates/new`        | Create form                              |
| POST   | `/certificates`            | Create (validates and redirects)         |
| GET    | `/certificates/{id}`       | Detail page                              |
| GET    | `/certificates/{id}/edit`  | Edit form                                |
| POST   | `/certificates/{id}`       | Update                                   |
| GET    | `/certificates/{id}/delete`| Delete confirmation page                 |
| POST   | `/certificates/{id}/delete`| Delete                                   |
| POST   | `/demo/reset`              | Reset demo dataset (flash message)       |
| GET    | `/actuator/health`         | Health probe                             |

## Configuration

| Property / env var                       | Default                          | Meaning                          |
|------------------------------------------|----------------------------------|----------------------------------|
| `app.warning-threshold-days` / `WARNING_THRESHOLD_DAYS` | 30               | Expiring-soon window (>= 0)      |
| `app.demo-data.enabled` / `DEMO_DATA_ENABLED`           | true             | Seed the demo dataset            |
| `SERVER_PORT`                            | 8080                            | HTTP port                        |
| `SPRING_DATASOURCE_URL`                  | `jdbc:h2:file:./data/...`       | Any JDBC URL (PostgreSQL profile available) |
| `THYMELEAF_CACHE`                        | false (dev)                     | Template caching                 |

## Project layout

```
src/main/java/com/example/certmonitor/
  config/       AppProperties, DemoDataInitializer
  domain/       Certificate entity + enums (type, status, environment)
  repository/   CertificateRepository (JPA Specifications)
  service/      CertificateService, ExpiryStatusCalculator, DemoDataService, views
  web/          Dashboard/Certificate/DemoData controllers, form DTO, error handling
src/main/resources/templates/   Thymeleaf pages + shared fragments
src/test/java/...               unit, MockMvc, repository and Selenium tests
docs/                           assignment documentation (see docs/README.md)
Dockerfile, docker-compose.yml  container build and run
Jenkinsfile                     CI/CD pipeline
ansible/                        provisioning and deployment playbooks
scripts/                        run/test/deploy helpers (PowerShell and bash)
```

## Documentation

All deliverable documents live in [`docs/`](docs/README.md) - problem definition, agile plan,
SRS/architecture/data model/API routes/local setup, Git workflow, collaboration, Jenkins,
test plan, Docker, continuous delivery, Ansible provisioning, final validation and the demo script.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Short version: create a feature branch, keep commits
small, make sure `./mvnw -B test` passes, and open a pull request with the checklist filled in.
