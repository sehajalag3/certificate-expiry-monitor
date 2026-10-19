# 14. Selenium test plan

## 14.1 Objective

Prove the five most important user journeys work in a real browser against a running instance
of the application (not mocks): dashboard truth, create, edit, search/filter, validation + delete.

## 14.2 Scope

**In scope:** rendered HTML, form submission and redirect/flash behaviour, navigation,
client-visible validation messages, demo data reset, health of static assets.

**Out of scope:** cross-browser matrix (Chrome only in v1), visual/pixel regression,
load testing, API security testing (covered by MockMvc/integration tests).

## 14.3 Test environment

| Aspect | Choice |
|--------|--------|
| Runner | JUnit 5 + Selenium 4.27, Maven profile `ui` |
| Browser | Google Chrome (155.x on the dev machine), headless by default |
| Driver | Selenium Manager resolves the matching ChromeDriver automatically |
| App | `@SpringBootTest(webEnvironment = RANDOM_PORT)` on a random port |
| Database | isolated H2 in-memory (`jdbc:h2:mem:uitest`) per suite |
| Data | `POST /demo/reset` before every test (redirects not followed) so counts are deterministic |
| Class | `src/test/java/com/example/certmonitor/ui/CertificateUiTest.java` |
| Isolation | default surefire run **excludes** `**/ui/**`; only `-Pui` runs these tests |

Run:

```powershell
.\mvnw.cmd -B test -Pui                 # headless (default)
.\mvnw.cmd -B test -Pui -Dui.headless=false   # visible browser
```

## 14.4 Locator strategy

- Prefer stable hooks: `By.cssSelector("[data-testid='...']")`.
- Never index-based XPath or CSS children - template refactors must not break tests.
- Known hooks include: `card-total-value`, `card-expired-value`, `alert-row`, `cert-row`,
  `cert-name`, `row-edit`, `row-delete`, `search-input`, `filter-status`,
  `field-name`, `field-issue-date`, `submit-certificate`, `flash-success`,
  `form-error-summary`, `error-name`, `delete-cert-name`, `btn-confirm-delete`, `empty-state`.

Waiting: a single `WebDriverWait` (15 s, 500 ms polling) wrapper (`visible()` / `click()` /
`rowFor()`). No `Thread.sleep`. Row lookups retry on `StaleElementReferenceException`
because table navigations re-render the DOM mid-wait.

## 14.5 Journey catalogue

| # | Test | Steps | Key assertions |
|---|------|-------|----------------|
| 1 | `dashboardLoadsWithSummaryCards` | open `/` | cards = 10 / 2 / 4 / 4; at least one alert row; dashboard nav active; title contains "Certificate Expiry Monitor" |
| 2 | `addCertificateJourney` | `/certificates/new` -> fill all fields (issue = today-10, expiry = today+90) -> submit | redirects to `/certificates/{id}`; detail shows name, hostname, status **Valid**, owner; flash "was created" |
| 3 | `editCertificateJourney` | list -> search "Employee VPN" -> edit -> change owner -> submit | URL lands on detail; flash "was updated"; owner updated |
| 4 | `searchAndFilterByStatusJourney` | list (10 rows) -> search "wiki" (1 row) -> clear (10 rows) -> filter status EXPIRED (2 rows) | exact row counts; every badge in the filtered table reads "Expired" |
| 5 | `validationAndDeleteConfirmationJourney` | submit empty create form -> fix nothing -> go delete "SMTP Relay" -> cancel -> confirm | error summary visible; `error-name`/`error-hostname` messages; cancel returns to detail; confirm deletes and flashes "was deleted"; empty state appears when searching the deleted name |

Status rule boundaries (expiry today, threshold, threshold+1, zero threshold, negative
threshold) are covered faster and more precisely by `ExpiryStatusCalculatorTest` - browser
tests cover the wiring, unit tests cover the edges.

## 14.6 Failure handling

`TestExecutionExceptionHandler` captures, before rethrowing:

- `target/selenium-screenshots/<test-name>.png` - full page screenshot
- `target/selenium-screenshots/<test-name>.html` - page source dump

Both are archived by Jenkins (`archiveArtifacts 'target/selenium-screenshots/**'`).

## 14.7 Entry / exit criteria

**Entry:** `./mvnw -B test` green; demo data seedable; Chrome available.
**Exit (v1):** all 5 journeys green headless, twice in a row, on the dev machine.

**Actual result:** 5/5 passing headless (Chrome 155) - see `docs/20-final-validation.md`.

## 14.8 Flake counter-measures already in place

1. Deterministic data via `POST /demo/reset` (client that does **not** follow redirects -
   a followed redirect would hit `/;jsessionid=...` and lose the flash).
2. ISO `yyyy-MM-dd` form dates (`@DateTimeFormat`) so `<input type="date">` never renders
   an empty value regardless of machine locale.
3. Stale-element-safe row lookup after navigations.
4. Content anchors (`detail-name`) before asserting transient flash messages.
5. Headless mode + fixed window size (1440x1000) for stable layout.
