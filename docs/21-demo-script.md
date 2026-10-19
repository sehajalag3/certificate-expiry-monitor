# 21. Demo script

**Duration:** ~8 minutes. **Audience:** course reviewer / stakeholder.
**Goal:** show that the application solves the problem (invisible certificate expiry) and that
it is engineered end to end (tests, container, pipeline).

## Before the demo (2 minutes)

```powershell
.\mvnw.cmd -B spring-boot:run          # or: .\scripts\deploy-docker.ps1
# wait for "Started CertificateExpiryMonitorApplication"
```

Open **http://localhost:8080** in a browser. Optional second tab: `http://localhost:8080/actuator/health`.

---

## 1. The problem (30 s)

> "Certificates expire silently. When one does, sites and APIs break. This app keeps the whole
> inventory visible and screams before the date arrives."

## 2. Dashboard - value in one screen (60 s)

Point at the four cards: **10 total - 2 expired - 4 expiring soon - 4 valid** (all computed,
not typed). Highlight the **expiry alerts** table ordered by urgency and the navbar links.

Expected: counts render instantly; alert rows exist.

## 3. Find the risky certificates (60 s)

1. Click **Expiry exceptions** (`/exceptions`) - only expired + expiring soon, with counts.
2. Back to **Certificates**, filter **Status = Expired** -> exactly 2 rows with red badges.
3. Search `vpn` -> the Employee VPN certificate; clear -> 10 rows again.
4. Change sort to **Expiry date** and flip direction - the table reorders.

Expected: filters combine with search; page size options 5/10/20/50 work.

## 4. Manage a certificate (2 min)

1. **Add certificate** -> submit empty form first: error summary + field messages appear
   (`Name is required`, hostname pattern) and nothing is saved.
2. Fill: name `Demo TLS Cert`, hostname `demo.internal.example.com`, owner `Platform Team`,
   environment Staging, issue = today - 30, expiry = today + 10 -> submit.
3. Detail page shows **Expiring soon**, days remaining, flash `was created`.
4. **Edit** -> change owner to `Security Team` -> submit -> flash `was updated`.
5. **Delete** -> confirmation page shows the certificate name -> **Confirm** -> flash
   `was deleted`; search for the name -> empty state.

Talking points: validation happens on the server; the status is derived, never stored; delete
always requires confirmation.

## 5. Status rule proof (60 s)

In a terminal while the app runs:

```powershell
.\mvnw.cmd -B test "-Dtest=ExpiryStatusCalculatorTest"
```

> "Expiry today counts as expiring soon, day 30 (the threshold) is still a warning, day 31 is
> valid, and the threshold itself is configurable - the tests pin these boundaries."

(Optionally show `WARNING_THRESHOLD_DAYS=7` run of the app and watch the dashboard reclassify.)

## 6. Engineering story (2 min)

Show, quickly:

```powershell
.\mvnw.cmd -B test          # 37 tests green
.\mvnw.cmd -B test -Pui     # 5 Selenium journeys in headless Chrome
```

Then the pipeline and delivery artefacts:

- `Jenkinsfile` - test -> UI -> package -> image -> (optional) push/deploy
- `Dockerfile` + `docker-compose.yml` - already built image, healthcheck `healthy`
- `ansible/` - provisioning + systemd deployment with a health gate
- `docs/` - 21 documents (SRS, architecture, data model, API routes, test plan, validation report)

## 7. Container variant (optional, 60 s)

```powershell
docker compose up -d --build
docker ps                    # STATUS: healthy
curl http://localhost:8080/actuator/health   # {"status":"UP"}
```

## Reset / recovery during the demo

- Click **Reset demo data** (`POST /demo/reset`) or:
  `curl -X POST http://localhost:8080/demo/reset` - back to the 10 baseline records.
- Nuclear option: stop the app, delete the `data/` folder, restart.

## Questions to expect

| Question | Answer |
|----------|--------|
| Where are the private keys? | Nowhere - metadata only; see "out of scope" in the problem definition |
| Why H2? | zero-install demo; PostgreSQL profile for production |
| Why server-rendered? | simple to operate and to test end to end with Selenium |
| How is status stored? | It isn't - computed from expiry date + threshold + injected clock |
| What if a date field breaks in some locale? | forms post ISO `yyyy-MM-dd` (regression covered by UI journey) |
| Is CI real? | every pipeline command was executed locally; the Jenkins run needs a Jenkins agent (documented) |
