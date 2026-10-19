# 4. Requirements catalogue

## 4.1 Functional requirements (acceptance criteria)

**FR1 - Status computation**
- Given an expiry date strictly before today, the status is EXPIRED.
- Given an expiry date equal to today, the status is EXPIRING_SOON (a certificate dies today).
- Given `0 <= daysRemaining <= threshold`, status is EXPIRING_SOON.
- Given `daysRemaining > threshold`, status is VALID.
- `daysRemaining` is the whole-day difference `expiryDate - today`.
- AC: verified by `ExpiryStatusCalculatorTest` boundary cases (threshold 30 and 0).

**FR2 - Threshold configuration**
- Default 30; settable via `app.warning-threshold-days` / `WARNING_THRESHOLD_DAYS`.
- Value 0 is legal (only expired + today count as warnings); negative values are rejected
  (`IllegalArgumentException` from the calculator, `@Min(0)` config validation at startup).

**FR3 - Dashboard**
- Shows four cards: total, expired, expiring soon, valid; counts always sum to the total.
- Shows expiry alerts (expired first, then soonest expiry).
- AC: `GET /` returns 200 with `10 / 2 / 4 / 4` for the demo dataset (MockMvc + Selenium).

**FR4-FR7 - List management**
- `q` matches name, hostname, issuer, owner, notes (case-insensitive).
- `status`, `type`, `environment` filters combine with `q` (logical AND).
- Unknown enum values in filters must not 500 - they are ignored and the full result is shown.
- `sort` must come from the allow-list; `direction` is `asc`/`desc` (default `asc`).
- `page` is 0-based, `size` from {5,10,20,50}; out-of-range values fall back to defaults.
- AC: `CertificateRepositoryTest`, `CertificateWebIntegrationTest`.

**FR8-FR10 - Write paths**
- Name (2-100), hostname (pattern incl. `*.example.com` and `localhost`), owner (required),
  type/environment required, `expiryDate > issueDate`, `issueDate <= today`.
- Validation errors redisplay the form with an error summary and per-field messages; nothing
  is persisted.
- Delete requires visiting the confirmation page first.
- AC: MockMvc tests for each error branch + Selenium journeys for add/edit/delete.

**FR11-FR12 - Reading paths**
- Detail page shows computed status, days remaining, timestamps.
- `/exceptions` shows only EXPIRED and EXPIRING_SOON certificates.

**FR13 - Demo data**
- Seed only when the table is empty at startup and `app.demo-data.enabled=true`.
- Dataset: 10 certificates generated from `LocalDate.now()` offsets
  (2 expired: -45, -5; 4 expiring soon: today, +7, +20, +30; 4 valid: +60, +120, +180, +365).
- `POST /demo/reset` deletes and reseeds, then redirects with a flash message.
- AC: `DemoDataIntegrationTest`, dashboard counts asserted in Selenium.

**FR14 - Errors**
- Unknown certificate id, unknown path and mismatched path variables -> 404 error page.
- Unexpected exceptions -> 500 error page with no stack traces leaked to the client.

**FR15 - Health**
- `/actuator/health` returns `{"status":"UP"}` and liveness/readiness groups for probes.

## 4.2 Non-functional requirements

| Category | Requirement | Verification |
|----------|-------------|--------------|
| Performance | Dashboard and list render from in-memory/file H2 in < 500 ms locally | manual smoke timings (pages ~10 KB, < 1 s incl. cold start) |
| Reliability | Graceful shutdown drains in-flight requests | `server.shutdown: graceful` |
| Portability | Windows/Linux/macOS dev, any x86-64 Linux host in Docker | commands verified on Windows + Linux image build |
| Testability | Business rules testable without HTTP; routes testable without a browser | 37 tests default run |
| Usability | Risky certificates visible on the first screen; three clicks from dashboard to delete | Selenium journeys + demo script |
| Security | No secrets in code, no stack traces to clients, validation on all writes, non-root container | configuration review + tests |
| Maintainability | Layered package structure, no business logic in templates | code review checklist in `CONTRIBUTING.md` |

## 4.3 Data requirements
- Persist all certificate fields plus `createdAt`/`updatedAt`.
- Survive restarts (file DB / volume / PostgreSQL).
- Resettable demo dataset must not overwrite a pre-populated production database
  (seed only if empty).

## 4.4 Interfaces
- HTML routes listed in `docs/07-api-routes.md`; JSON at `/actuator/health`.
- Configuration via environment variables (see README table).
