# 15. Continuous testing strategy

## 15.1 Test pyramid for this project

```
        /  5 Selenium   \        browser journeys (ui profile)      - slowest, highest confidence
       /  15 MockMvc +    \      route/integration tests            - full Spring context
      /   5 repository      \    @DataJpaTest (Specifications)      
     /   17 unit             \   calculator/service/form logic      - fastest, majority
```

Counts as implemented: **37 tests in the default run** (7 calculator + 10 service +
5 repository + 12 MockMvc + 3 demo-data) and **5 Selenium journeys** = **42 total**.

## 15.2 What each layer guarantees

| Layer | Class | Guarantees |
|-------|-------|------------|
| Unit | `ExpiryStatusCalculatorTest` | exact status boundaries: expired (<0), expiry-today (soon), threshold (soon), threshold+1 (valid), zero threshold, negative threshold rejected |
| Unit | `CertificateServiceTest` | CRUD mapping, counts, alerts, search/filter/sort/pagination behaviour with a pinned `Clock` (2026-10-19) and Mockito |
| Integration | `CertificateRepositoryTest` (`@DataJpaTest`) | Specification queries, combined filters, ordering, pagination against a real schema (H2 in-memory) |
| Integration | `CertificateWebIntegrationTest` (`@SpringBootTest` + MockMvc) | every route: happy paths + validation errors + unknown id 404 + dashboard counts + health UP |
| Integration | `DemoDataIntegrationTest` | seed-if-empty, reset -> exactly 10 records with expected status distribution |
| E2E | `CertificateUiTest` (`-Pui`) | the five journeys in `docs/14-selenium-test-plan.md` in a real browser |

## 15.3 Where tests run

| Trigger | Command | Scope |
|---------|---------|-------|
| Developer loop (pre-commit) | `./mvnw -B test` | 37 fast tests, no browser |
| Developer loop (UI change) | `./mvnw -B test -Pui` | + 5 journeys |
| Helper scripts | `scripts/run-tests.ps1 [-Ui]`, `scripts/run-tests.sh [--ui]` | same commands, wrapped |
| Jenkins stage 1 | `./mvnw -B clean test` | gate for everything else |
| Jenkins stage 2 | `./mvnw -B test -Pui` | toggle `RUN_UI_TESTS` |
| Docker image build | `mvn -DskipTests package` | tests already ran earlier in the pipeline |

## 15.4 Quality gates

1. **Merge gate:** `./mvnw -B test` green on `main` (always) and `-Pui` green when
   templates/CSS/controllers changed.
2. **Pipeline gate:** unit stage failure stops the pipeline before any image is built.
3. **Coverage intent (manual):** all business rules, all routes, all form error branches,
   all status boundaries have tests; `CONTRIBUTING.md` requires tests with new behaviour.
4. **No flaky tests:** deterministic clocks/dates, in-memory DBs, no sleeps; a red UI test is
   treated as a bug in test or code, never "re-run until green".

## 15.5 Reporting

- Surefire XML -> JUnit trend in Jenkins (`junit 'target/surefire-reports/*.xml'`).
- Selenium failures produce PNG + HTML artifacts archived with the build.
- Local reports: `target/surefire-reports/*.txt`.

## 15.6 Known gaps (documented honestly)

- No mutation testing / coverage percentage tool wired in (would add JaCoCo in a next sprint).
- No cross-browser matrix (Firefox/Edge) - Chrome only.
- PostgreSQL profile path is not exercised by automated tests on this machine.
