# 8. Local setup guide

## 8.1 Prerequisites

| Tool | Version used | Required for |
|------|--------------|--------------|
| JDK | 21 (tested: Microsoft OpenJDK 21.0.12.1) | building and running |
| Maven | **not required** - Maven Wrapper 3.9.9 downloads itself | everything |
| Git | 2.47.x | source control |
| Google Chrome | 155.x (any recent Chrome) | `ui` Selenium tests only |
| Docker Desktop | engine 29.6.2 (optional) | container build/run |
| Node/Python/etc. | - | not used |

Check: `java -version`, `git --version`, `docker version`.

## 8.2 First run

```powershell
git clone <repo-url> certificate-expiry-monitor
cd certificate-expiry-monitor

# Windows
.\mvnw.cmd -B spring-boot:run
# Linux/macOS
./mvnw -B spring-boot:run
```

Open http://localhost:8080 - the demo dataset (10 certificates) is seeded automatically on
first start. Stop with Ctrl+C (graceful shutdown).

> On the very first `mvnw` invocation the wrapper downloads the Maven distribution
> (one-time, ~10 MB) into your local Maven repository.

## 8.3 Run the tests

```powershell
.\mvnw.cmd -B test        # 37 unit + integration tests, no browser
.\mvnw.cmd -B test -Pui   # + 5 Selenium journeys (Chrome, headless by default)
.\mvnw.cmd -B test -Pui -Dui.headless=false   # watch the browser
```

Or use the helpers:

```powershell
.\scripts\run-tests.ps1           # default suite
.\scripts\run-tests.ps1 -Ui       # + Selenium
.\scripts\run-tests.ps1 -Ui -Visible
```

Failure artifacts: `target/surefire-reports/`, `target/selenium-screenshots/*.png|html`.

## 8.4 Build & run the jar

```powershell
.\mvnw.cmd -B -DskipTests package
java -jar target\certificate-expiry-monitor-1.0.0.jar
```

## 8.5 Run with Docker

```powershell
.\scripts\deploy-docker.ps1          # docker compose up -d --build + health wait
# or manually
docker compose up -d --build
docker compose logs -f certificate-monitor
docker compose down                   # data stays in the certificate-data volume
```

## 8.6 Configuration

| Env var | Default | Effect |
|---------|---------|--------|
| `SERVER_PORT` | 8080 | HTTP port |
| `WARNING_THRESHOLD_DAYS` | 30 | expiring-soon window |
| `DEMO_DATA_ENABLED` | true | seed/reset demo dataset |
| `SPRING_DATASOURCE_URL` | `jdbc:h2:file:./data/certificate-monitor;AUTO_SERVER=TRUE` | any JDBC URL |
| `SPRING_PROFILES_ACTIVE` | - | `postgres` for PostgreSQL |
| `THYMELEAF_CACHE` | false | set `true` in production |
| `H2_CONSOLE_ENABLED` | false | `/h2-console` (dev only) |

PowerShell example:

```powershell
$env:SERVER_PORT='9090'; $env:WARNING_THRESHOLD_DAYS='14'; .\mvnw.cmd -B spring-boot:run
```

## 8.7 PostgreSQL (optional, production path)

```yaml
# application-postgres.yml (bundled) - activate with SPRING_PROFILES_ACTIVE=postgres
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/certmonitor
SPRING_DATASOURCE_USERNAME=certmonitor
SPRING_DATASOURCE_PASSWORD=...
```

The PostgreSQL driver is a `runtime` dependency; the profile was **not executed** on the dev
machine (H2 path is the verified one) - see `docs/20-final-validation.md`.

## 8.8 Troubleshooting

| Symptom | Fix |
|---------|-----|
| `java` not found | install JDK 21 and ensure `JAVA_HOME`/`PATH` point to it |
| Port 8080 busy | `SERVER_PORT=8081` or stop the other process |
| Chrome tests fail to start a driver | Chrome installed? Selenium Manager downloads the matching driver automatically; corporate proxies may need `HTTP(S)_PROXY` |
| Dates show validation errors after an edit | forms post ISO `yyyy-MM-dd`; don't hand-edit the HTML |
| Demo data not seeding | `DEMO_DATA_ENABLED=true` and the `data/` database must be empty (`DELETE` the `data/` folder to reset) |
