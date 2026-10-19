# 11. Collaboration

How a small team works together on the Certificate Expiry Monitor without stepping on each
other's commits.

## 11.1 Communication rituals

| Ritual | When | Output |
|--------|------|--------|
| Stand-up | daily, 5 min | what's merged, what's blocked, next branch |
| Sprint review | end of sprint | working demo against acceptance criteria (use `docs/21-demo-script.md`) |
| Code review | on every change to `main` | approved PR / review comments |
| Incident retro | after any bug escapes to a demo | new test case + backlog entry |

## 11.2 Branch + PR flow

1. Create `feature/...` from `main` (see `10-feature-branch-guide.md`).
2. Open a PR with the repository template (`.github/PULL_REQUEST_TEMPLATE.md`) filled in.
3. CI (Jenkins) must be green: unit/integration stage always, Selenium stage for UI changes.
4. One approval from a teammate; author merges with `--no-ff` (or squash on GitHub).
5. Delete the branch.

Reviewers are expected to respond within one working day; keep PRs under ~400 changed lines.

## 11.3 Code review checklist

**Correctness**
- [ ] business rules live in `service/`, not controllers or templates
- [ ] status/date logic uses the injected `Clock`, never `LocalDate.now()` in services
- [ ] every write path is validated (`CertificateForm`) and error branches have tests

**Tests**
- [ ] new logic has unit tests; routes have MockMvc tests; UI flows have Selenium journeys
- [ ] `./mvnw -B test` passes locally (and `-Pui` when UI changed)

**Security & ops**
- [ ] no secrets/credentials/absolute local paths
- [ ] no stack traces or internal messages exposed to users
- [ ] config changes are env-var driven with safe defaults

**Maintainability**
- [ ] naming matches existing patterns (`*Controller`, `*Service`)
- [ ] `data-testid` attributes preserved for Selenium
- [ ] docs (`docs/`, README) updated when behaviour, routes or config change

## 11.4 Shared conventions

- **Formatting:** default IDE formatting; no repo-wide reformat commits.
- **Language:** user-facing strings in English; imperative commit messages.
- **Tests:** deterministic - pinned `Clock`, in-memory databases, no `Thread.sleep`.
- **Assets:** Bootstrap is vendored (`static/vendor/`), no CDN links.
- **Data:** never commit `data/` (H2 files) or screenshots outside `target/`.

## 11.5 Resolving conflicts

```bash
git switch feature/x
git fetch                      # when a remote exists
git rebase main
# resolve, then
git rebase --continue
./mvnw -B test
git push --force-with-lease    # only your own branch
```

Conflict hot spots: `pom.xml`, `docs/README.md`, `Jenkinsfile` - merge those first, smallest
diff first.

## 11.6 Issue hygiene

- Bug -> bug report template, reproduction steps, Java version, run mode (jar/Maven/Docker).
- Feature -> feature request template, note DB/route/UI impact.
- Every escaped bug gets a regression test in the same PR that fixes it.
