# 10. Feature branch guide

A step-by-step playbook for taking a ticket from backlog to merged `main`.

## 1. Pick up the story

- Move the story to *In progress* on the board (see `02-agile-plan.md`).
- Note its acceptance criteria - they become your tests.

## 2. Create the branch

```bash
git switch main && git pull --ff-only     # when a remote exists
git switch -c feature/<ticket-id>-<short-kebab>
```

Examples: `feature/S6-status-filter`, `bugfix/S3-date-format`.

## 3. Implement

- Follow the layering in `05-architecture.md`: business rules in `service/`, mapping in
  controllers/DTOs, display only in templates.
- Keep the diff small; if the change grows, split it into follow-up branches.

## 4. Test as you go

```bash
./mvnw -B test              # must stay green throughout
./mvnw -B test -Pui         # when templates/CSS/controllers changed
```

Rules of thumb:
- new business rule -> unit test with pinned `Clock`
- new/changed route -> MockMvc test (happy path **and** error path)
- changed page -> assert the existing Selenium journey still passes (add one if the flow is new)

## 5. Commit

```bash
git add -p                                  # stage hunks deliberately
git commit -m "Add status filter to certificate list"
git commit -m "Test status filter combined with search"   # tests with their feature
```

## 6. Self-review before asking anyone

```bash
git diff main...HEAD          # everything your branch changes
git log --oneline main..HEAD  # commit story
```

Checklist: no debug prints, no dead code, no absolute paths, no secrets, docs updated if
routes/config changed.

## 7. Merge

GitHub flow (remote configured on this project):

```bash
git push -u origin feature/<ticket-id>-<short-kebab>   # open a PR from the branch
# ... CI green, review comments addressed ...
git switch main
git pull --ff-only
git merge --no-ff feature/<ticket-id>-<short-kebab>
git push origin main
git branch -d feature/<ticket-id>-<short-kebab>
git push origin --delete feature/<ticket-id>-<short-kebab>
./mvnw -B test                # final gate on main
```

Offline fallback: run the same `merge --no-ff` locally without the push lines. Use
`.github/PULL_REQUEST_TEMPLATE.md` for the PR description; the review checklist is in
`11-collaboration.md` §11.3.

## 8. Hotfix variant

```bash
git switch -c hotfix/fix-health-probe main
# fix + test
git switch main && git merge --no-ff hotfix/fix-health-probe
git tag -a v1.0.1 -m "Hotfix: health probe"
```

## 9. Rules that keep the branch healthy

1. `main` must always pass `./mvnw -B test` - never merge red.
2. Delete branches after merge (no long-lived debris).
3. Rebase long-lived branches rather than merging `main` into them repeatedly.
4. Never commit generated files (`target/`, `data/`, screenshots).
5. A branch that stalls > 3 days gets either merged, split or closed with a note.
