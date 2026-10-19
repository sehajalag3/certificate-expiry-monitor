# 16. Docker

## 16.1 Files

| File | Purpose |
|------|---------|
| `Dockerfile` | multi-stage build: Maven 3.9.9 + JDK 21 -> JRE 21 runtime (non-root, curl, healthcheck) |
| `.dockerignore` | keeps `target/`, `data/`, docs, git metadata out of the build context |
| `docker-compose.yml` | one service, published port, named volume, env config, restart policy |
| `deploy/docker-compose.deploy.yml` | deployment stack: app container + Nginx reverse proxy, image tag via `IMAGE_REF`, proxy port via `HTTP_PORT` |
| `deploy/nginx/nginx.conf` | Nginx site: proxies to the app on `certificate-monitor:8080`, `X-Forwarded-*` headers, `/nginx-health` self-check |
| `scripts/deploy-docker.ps1` / `.sh` | build + start + wait for health |

## 16.2 Image design

**Build stage** (`maven:3.9.9-eclipse-temurin-21`)
- copies `pom.xml` + `src`, runs `mvn -B -DskipTests package`
- tests already ran in CI before this stage; the image build never depends on a browser

**Runtime stage** (`eclipse-temurin:21-jre-jammy`)
- `curl` installed for the healthcheck, non-root `app` user, `WORKDIR /app`
- jar copied as `/app/app.jar`, `VOLUME /app/data` for the H2 database
- `EXPOSE 8080`, `HEALTHCHECK` hits `/actuator/health` (30 s interval, 45 s start period)
- `ENTRYPOINT sh -c "exec java $JAVA_OPTS -jar /app/app.jar"` so memory can be tuned via
  `JAVA_OPTS` (Ansible sets `-Xms128m -Xmx512m`)

## 16.3 Usage

```bash
# build
docker build -t certificate-expiry-monitor:1.0.0 .

# run
docker run -d --name certificate-expiry-monitor -p 8080:8080 \
  -v certificate-data:/app/data \
  certificate-expiry-monitor:1.0.0

# compose (preferred)
docker compose up -d --build
docker compose ps
docker compose logs -f certificate-monitor
docker compose down          # volume (data) survives
```

Windows helper: `.\scripts\deploy-docker.ps1 -Port 8080`.

## 16.4 Configuration via environment

```yaml
environment:
  SERVER_PORT: "8080"
  WARNING_THRESHOLD_DAYS: "30"
  DEMO_DATA_ENABLED: "true"     # set false in production
  THYMELEAF_CACHE: "true"
```

Any `SPRING_*` variable also works (e.g. `SPRING_DATASOURCE_URL` pointing at PostgreSQL).

## 16.5 Security notes

- Container runs as non-root `app` user.
- No secrets baked into the image; `.dockerignore` excludes docs/CI files from the context.
- Only port 8080 is published; put a reverse proxy/TLS terminator in front for public exposure.
- H2 console stays disabled by default.

## 16.6 Verified on this machine

| Check | Result |
|-------|--------|
| `docker build -t certificate-expiry-monitor:1.0.0 .` | **success** (Maven build inside container) |
| `docker compose config -q` | **valid** |
| `docker run -d -p 8081:8080 ...` | container started, `Started CertificateExpiryMonitorApplication in 6.5 s` |
| `/actuator/health` from the host | `{"status":"UP","groups":["liveness","readiness"]}` |
| Docker `HEALTHCHECK` state | **healthy** |
| Dashboard via container | `HTTP 200`, title `Dashboard | Certificate Expiry Monitor` |

## 16.7 Troubleshooting

| Symptom | Fix |
|---------|-----|
| healthcheck `start period` exceeded | first start downloads nothing but H2 init + JPA; raise `--start-period` on slow hosts |
| `permission denied` writing `./data` | ensure the volume is owned by uid of `app` (compose/Ansible handle this) |
| Port conflict | change the left side of `8080:8080` or `SERVER_PORT` |
| Image rebuild uses cache too aggressively | `docker build --no-cache .` |
