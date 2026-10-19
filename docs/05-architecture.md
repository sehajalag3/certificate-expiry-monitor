# 5. Architecture

## 5.1 Architectural style

Classic **layered server-rendered monolith** with MVC on the web tier and a thin domain/service
tier. Chosen because the app is CRUD-heavy, low write-concurrency, and must be trivially
testable end to end (Selenium) and deployable (single jar / single container).

```
Browser ── HTTP ──> Thymeleaf templates (server-rendered HTML + Bootstrap 5)
                        ▲                     │
                        │ view models          │ form POSTs
        ┌───────────────┴─────────────────────▼─────────────────┐
        │  Web layer        controllers + form DTO + @ControllerAdvice
        ├────────────────────────────────────────────────────────┤
        │  Service layer    CertificateService, DemoDataService, │
        │                   ExpiryStatusCalculator, views        │
        ├────────────────────────────────────────────────────────┤
        │  Persistence      Spring Data JPA (Specifications)      │
        │                   CertificateRepository                 │
        ├────────────────────────────────────────────────────────┤
        │  Data store       H2 file (default) / PostgreSQL        │
        └────────────────────────────────────────────────────────┘
```

## 5.2 Components

| Component | Package | Responsibility |
|-----------|---------|----------------|
| Application + `Clock` bean | `com.example.certmonitor` | bootstrap; injectable clock keeps status logic deterministic in tests |
| `AppProperties` | `config` | binds `app.*` (threshold, demo flag) with `@Validated`/`@Min(0)` |
| `DemoDataInitializer` | `config` | `ApplicationRunner`, `@ConditionalOnProperty` - seeds demo data when enabled and empty |
| `Certificate` + enums | `domain` | JPA entity, `@PrePersist`/`@PreUpdate` timestamps, status/type/environment enums |
| `CertificateRepository` | `repository` | `JpaRepository` + derived dashboard counts + expiry alert query |
| `ExpiryStatusCalculator` | `service` | pure function: status + days remaining from expiry date/today/threshold |
| `CertificateService` | `service` | CRUD, Specification-based search/filter, sorting allow-list, pagination, counts, alerts, entity<->form mapping |
| `DemoDataService` | `service` | seed-if-empty / reset / count for the demo dataset |
| `CertificateView`, `DashboardCounts` | `service` | immutable-ish view models consumed by templates (plain getters) |
| `CertificateForm` | `web.dto` | bean-validation DTO for create/edit incl. `@AssertTrue` cross-field checks |
| Controllers | `web` | route mapping, binding, flash messages; no business rules |
| `GlobalExceptionHandler` | `web` | 404 (not found / bad path variable / unknown resource) and 500 mapping |
| Templates | `resources/templates` | `fragments/layout.html` (nav, flash, badges, footer) + pages; stable `data-testid` hooks for Selenium |

## 5.3 Key design decisions

1. **Status computed at read time, not stored.** Storing status would drift as "today" moves;
   computing from `expiryDate` + injected `Clock` is always correct and trivially unit-testable.
2. **`Clock` bean instead of `LocalDate.now()`** in services - tests pin the clock
   (`Clock.fixed(...)`) so boundary tests never flake at midnight.
3. **Specifications for list filtering** - keeps query composition out of the controller and
   makes the repository independently testable with `@DataJpaTest`.
4. **Form DTO separate from entity** - validation messages, cross-field `@AssertTrue` rules and
   future field additions don't leak into the persistence model.
5. **Sorting allow-list** - prevents arbitrary property injection into `Sort`.
6. **Vendored Bootstrap, no CDN** - deterministic UI for Selenium and offline operation.
7. **Demo mode as configuration** (`app.demo-data.enabled`) - evaluation convenience without
   polluting production data.
8. **UI tests behind a Maven profile** - the default build never needs a browser, so CI can run
   unit/integration stages fast and gate the Selenium stage separately.

## 5.4 Runtime views

**Dev:** `mvnw spring-boot:run` -> H2 file `./data/certificate-monitor`, port 8080.

**Container:** multi-stage Dockerfile (Maven build -> JRE runtime, non-root, healthcheck),
`docker-compose.yml` mounts named volume `certificate-data` at `/app/data`.

**Pipeline:** Jenkins runs test -> ui test -> package -> image build -> (optional) push ->
(optional) Ansible deploy; the same commands a developer runs locally.

## 5.5 Module/build structure

Single Maven module (`pom.xml`, Spring Boot 3.5.5 parent). Surefire excludes `**/ui/**` by
default; the `ui` profile re-includes them and adds `ui.headless` (default `true`).
Selenium 4.27 is a regular test-scoped dependency so test compilation always works; profile
flags only decide whether browser tests *execute*.
