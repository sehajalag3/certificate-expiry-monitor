# 7. HTTP routes (UI + operational)

All UI routes return server-rendered HTML (Thymeleaf) unless stated otherwise.
Base URL: `http://localhost:8080` (configurable via `SERVER_PORT`).

## 7.1 Page routes

| Method | Path | Controller | Purpose |
|--------|------|------------|---------|
| GET | `/` | `DashboardController.dashboard` | Dashboard: summary cards (total/expired/expiring/valid), expiry alerts |
| GET | `/exceptions` | `DashboardController.exceptions` | Only expired + expiring-soon certificates |
| GET | `/certificates` | `CertificateController.list` | Paginated list with search/filter/sort |
| GET | `/certificates/new` | `CertificateController.createForm` | Empty create form (defaults: TLS_SSL, PRODUCTION, issue=today, expiry=+1y) |
| POST | `/certificates` | `CertificateController.create` | Validate + persist -> 303 to detail page (flash success) or re-render with errors |
| GET | `/certificates/{id}` | `CertificateController.detail` | Detail page (status, days remaining, timestamps) |
| GET | `/certificates/{id}/edit` | `CertificateController.editForm` | Prefilled edit form |
| POST | `/certificates/{id}` | `CertificateController.update` | Validate + persist -> 303 to detail (flash success) or re-render with errors |
| GET | `/certificates/{id}/delete` | `CertificateController.deleteConfirmation` | Confirmation page ("Delete <name>?") |
| POST | `/certificates/{id}/delete` | `CertificateController.delete` | Delete -> 303 to `/certificates` (flash success) |
| POST | `/demo/reset` | `DemoDataController.reset` | Reseed demo dataset -> 302 to `/` (flash success); 302 + warning if demo mode is off |

Errors: `GET /certificates/999` -> 404 page; unknown paths -> 404 page; unexpected failures -> 500 page (`GlobalExceptionHandler`, no stack traces leaked).

## 7.2 Query parameters for `GET /certificates`

| Param | Type / values | Default | Behaviour on bad input |
|-------|---------------|---------|------------------------|
| `q` | free text | `""` | matched case-insensitively against name, hostname, issuer, owner, notes |
| `status` | `EXPIRED`, `EXPIRING_SOON`, `VALID` | none | unknown value ignored (no 500) |
| `type` | `TLS_SSL`, `DOMAIN`, `CODE_SIGNING`, `CLIENT`, `EMAIL_SMTP`, `OTHER` | none | ignored |
| `environment` | `PRODUCTION`, `STAGING`, `DEVELOPMENT`, `QA` | none | ignored |
| `page` | integer, 0-based | `0` | negative clamped to `0` |
| `size` | `5`, `10`, `20`, `50` | `10` | anything else falls back to `10` |
| `sort` | `name`, `hostname`, `issuer`, `ownerTeam`, `type`, `environment`, `issueDate`, `expiryDate`, `createdAt`, `updatedAt` | `expiryDate` | not in allow-list -> `expiryDate` |
| `direction` | `asc`, `desc` | `asc` | anything else -> `asc` |

All filters combine with logical AND.

**Examples**

```
/certificates?q=vpn&status=VALID&sort=expiryDate&direction=asc&page=0&size=20
/certificates?status=EXPIRED&environment=PRODUCTION
/certificates?type=EMAIL_SMTP&sort=name&direction=desc
```

## 7.3 Operational endpoints

| Method | Path | Response |
|--------|------|----------|
| GET | `/actuator/health` | `200 {"status":"UP","groups":["liveness","readiness"]}` |
| GET | `/actuator/info` | app name/version/description |
| GET | `/h2-console` | H2 web console - **disabled by default** (`H2_CONSOLE_ENABLED=true` to enable; keep it off in production) |

## 7.4 Flash messages

Successful create/update/delete/reset set a `successMessage` flash attribute rendered as
`[data-testid="flash-success"]`; failures set `errorMessage` (`[data-testid="flash-error"]`).
These hooks are asserted by the Selenium journeys.

## 7.5 Form payloads (application/x-www-form-urlencoded)

Create/update post: `name`, `type`, `hostname`, `issuer`, `ownerTeam`, `environment`,
`issueDate` (ISO `yyyy-MM-dd`), `expiryDate` (ISO `yyyy-MM-dd`), `notes`.

Server-side validation rules (see `CertificateForm`): name 2-100 chars, hostname pattern
(`*.example.com`, `localhost` allowed), owner required, expiry after issue, issue date not in
future, notes <= 1000 chars. On failure the same form re-renders with
`[data-testid="form-error-summary"]` and per-field `error-*` messages; nothing is persisted.
