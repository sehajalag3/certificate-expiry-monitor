# 19. Reliability & operations

## 19.1 Failure modes and mitigations

| Failure | Impact | Mitigation | Detection |
|---------|--------|------------|-----------|
| Certificate expires unnoticed | outage | dashboard alerts + exceptions view, threshold config | UI, 404/5xx pages |
| App crash / JVM OOM | downtime | systemd `Restart=on-failure` (10 s), Docker `restart: unless-stopped`, `JAVA_OPTS` heap cap | systemd/Docker status, healthcheck |
| Container unhealthy | traffic to a dead app | Docker `HEALTHCHECK` on `/actuator/health`, Ansible health gate on deploy, orchestrator/compose restart | `docker ps`, pipeline stage |
| Data loss on restart | inventory lost | H2 file DB in `./data` / named volume / PostgreSQL; volume survives `docker compose down` | volume present, data after restart |
| Concurrent access (multi-user) | write conflicts | H2 `AUTO_SERVER=TRUE` for dev; PostgreSQL profile for production | docs |
| Bad deploy | broken UI | health gate blocks success; rollback to previous image tag | pipeline stage, health probe |
| Slow/blocked startup on deploy | false failure | `start-period=45s`, 20x5 s retry loop in Ansible | health output |
| Validation bypass via crafted POST | corrupt data | bean validation on every write path + service-level null checks | MockMvc negative tests |
| Locale-broken date inputs | unusable forms | fixed `yyyy-MM-dd` `@DateTimeFormat` on form fields | UI journey 5 |
| Secret leakage | security | no secrets in repo, `.gitignore`/`.dockerignore`, error details hidden (`include-message: never`, no stack traces) | config review |
| Flaky CI | lost trust in builds | pinned clock, in-memory DBs, stale-element-safe waits, artifacts on failure | test history |

## 19.2 Health & observability

- `/actuator/health` exposes overall status + `liveness`/`readiness` groups (probes enabled).
- `/actuator/info` reports app name/version/description for release identification.
- Structured console pattern: `timestamp level [thread] logger - message`.
- Container: `docker logs certificate-monitor`; host: `journalctl -u certificate-monitor`.
- Pipeline archives surefire XML + Selenium PNG/HTML per build.

## 19.3 Graceful operations

- `server.shutdown: graceful` - in-flight requests finish during stop (systemd `ExecStop`
  gives Docker 10 s via `docker stop -t 10`).
- Demo mode switch (`DEMO_DATA_ENABLED`) prevents accidental seeding in production.
- Seed-if-empty logic never overwrites an existing inventory.

## 19.4 Backup & recovery

| Store | Backup | Restore |
|-------|--------|---------|
| H2 file (default) | stop app, copy `data/` | copy back, start app |
| Docker volume `certificate-data` | `docker run --rm -v certificate-data:/data -v $PWD:/backup alpine tar czf /backup/cert-data.tgz -C /data .` | reverse the command |
| PostgreSQL | `pg_dump certmonitor` | `psql` restore + point `SPRING_DATASOURCE_URL` at it |

Recovery point objective in practice: last backup (v1 has no replication).

## 19.5 Capacity & performance

Single-node, in-memory/file-backed app sized for hundreds to low thousands of certificates.
Dashboard/list queries are simple scans/aggregations (<500 ms observed locally). Vertical
scaling (`JAVA_OPTS=-Xmx`) plus PostgreSQL covers growth; add reverse proxy caching for the
static assets (`Cache-Control` already configured for `spring.web.resources`, 3600 s).

## 19.6 Operational runbook (first 5 checks when something is wrong)

1. `curl -s localhost:8080/actuator/health` -> should be `UP`.
2. `docker ps` / `systemctl status certificate-monitor` -> restarts? exit codes?
3. `docker logs --tail 200 certificate-monitor` -> stack traces (500s also appear in the UI as
   the generic error page without details).
4. Disk/volume: is `data/` writable by the `app` user?
5. Threshold/demo env vars as expected? (`app.env` on provisioned hosts)
