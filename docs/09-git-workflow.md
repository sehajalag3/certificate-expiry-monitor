# 9. Git workflow

## 9.1 Repository

- Branch model: **trunk-based** - `main` is always releasable; short-lived feature branches.
- History: small, reviewed commits; no force-pushes on `main`; no `WIP` merges.

Initial layout created on the machine:

```bash
git init                                  # default branch: main
git add .
git status                                # review before committing
git commit -m "Add certificate expiry monitor application, tests and docs"
```

## 9.2 Branch naming

| Pattern | Use |
|---------|-----|
| `feature/<short-kebab>` | new functionality (`feature/expiry-email-alerts`) |
| `bugfix/<short-kebab>` | non-urgent fixes (`bugfix/empty-state-count`) |
| `hotfix/<short-kebab>` | urgent production fixes, branched from `main` |
| `docs/<short-kebab>` | documentation-only changes |
| `chore/<short-kebab>` | build/CI/tooling chores |

Full conventions: [`10-feature-branch-guide.md`](10-feature-branch-guide.md).

## 9.3 Everyday cycle

```bash
git switch main
git pull --ff-only                 # when a remote exists
git switch -c feature/my-change

# ... edit, then keep the build green ...
./mvnw -B test

git add src docs pom.xml
git commit -m "Add dashboard expiry alerts list"
git commit -m "Cover alert ordering with repository tests"   # separate concerns
git switch main
git merge --no-ff feature/my-change      # preserve branch context
git branch -d feature/my-change
```

## 9.4 Commit hygiene

- Imperative subject <= 72 chars: `Add hostname wildcard validation`.
- Body explains *why* when it is not obvious.
- One logical change per commit (code + its tests together).
- Never commit: `target/`, `data/`, `*.log`, IDE files, credentials (see `.gitignore`).
- Hooks (optional but recommended): run `./mvnw -B test` in `pre-push`.

## 9.5 Tagging releases

```bash
git tag -a v1.0.0 -m "Certificate Expiry Monitor 1.0.0"
git push origin v1.0.0        # when a remote exists
```

The Jenkins pipeline builds `1.0.0-${BUILD_NUMBER}` images; tags mark human milestones.

## 9.6 Working without a remote (this project)

The repository is local-only: there is no configured remote, so no `git push`/PR is performed.
Collaboration flows described in [`11-collaboration.md`](11-collaboration.md) apply once the
repo is published (e.g. to GitHub) - the same branch names, PR template and review checklist
are already provided under `.github/`.
