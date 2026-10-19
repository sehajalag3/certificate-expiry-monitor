# 22. Git collaboration log

A running record of the Git / GitHub collaboration exercises performed on this repository. It
exists so the workflow in [`09-git-workflow.md`](09-git-workflow.md),
[`10-feature-branch-guide.md`](10-feature-branch-guide.md) and
[`11-collaboration.md`](11-collaboration.md) is demonstrably exercised, not just described.

Remote: `https://github.com/sehajalag3/certificate-expiry-monitor.git` (`origin`)

## 22.1 Branch log

| # | Branch | Type | Change | Merge |
|---|--------|------|--------|-------|
| 1 | `docs/diagrams-and-workflow` | docs | Kanban board, DevOps lifecycle and use-case diagrams; remote workflow docs | merged to `main` with `--no-ff` |
| 2 | `docs/sprint-handover` | docs | handover notes drill | merged after conflict resolution |
| 3 | `docs/review-sla` | docs | review-SLA drill | merged after conflict resolution |

## 22.2 Merge-conflict drill

Branches 2 and 3 both rewrite the same line on purpose, producing a conflict that is resolved
during review (the resolution keeps both facts):

<!-- merge-conflict-drill -->
Practice-branch value: (set by branches 2 and 3)

## 22.3 Release baseline

`v1.0.0` is tagged on `main` once all drill branches are merged and `./mvnw -B test` is green:

```bash
git tag -a v1.0.0 -m "Certificate Expiry Monitor 1.0.0"
git push origin v1.0.0
```
