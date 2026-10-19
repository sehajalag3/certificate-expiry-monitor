# Contributing

Thanks for helping improve the Certificate Expiry Monitor.

## Ground rules

1. Branch from `main` using the naming convention in
   [`docs/09-git-workflow.md`](docs/09-git-workflow.md):
   `feature/short-description`, `bugfix/short-description`, `hotfix/short-description`.
2. Keep commits small and focused, written in the imperative mood
   ("Add hostname validation", not "Added...").
3. Never commit secrets, credentials, IDE files or `target/`.
4. Every change must leave the build green.

## Before you open a pull request

```powershell
.\mvnw.cmd -B test        # must pass (37 tests)
.\mvnw.cmd -B test -Pui   # must pass when you touched templates, CSS or controllers
```

Optional but recommended:

```powershell
.\mvnw.cmd -B -DskipTests package
```

## Pull request checklist

- [ ] Code compiles and `mvnw -B test` passes
- [ ] New behaviour has tests (unit for logic, MockMvc for routes)
- [ ] UI changes were checked with the `ui` Selenium profile
- [ ] Documentation in `docs/` updated if behaviour or routes changed
- [ ] No debug logging, commented-out code or personal paths left behind

## Reporting bugs

Use the bug report template and include:

- Exact steps to reproduce
- Expected vs. actual behaviour
- OS, Java version (`java -version`) and how you ran the app (jar, Maven, Docker)
- Any stack trace or screenshot (`target/selenium-screenshots/`)

## Code style

- Follow the existing package layout and naming (`Certificate*`, `*Controller`, `*Service`).
- Business rules live in `service/`, not in controllers or templates.
- Dates are formatted with `@DateTimeFormat(pattern = "yyyy-MM-dd")` so `<input type="date">`
  works regardless of machine locale.
- Templates expose stable `data-testid` attributes for Selenium - keep them when you edit pages.
