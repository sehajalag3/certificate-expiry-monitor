# 1. Problem definition

## Problem

Organisations run dozens of TLS/SSL, email, code-signing and domain certificates across web
servers, APIs, VPNs, load balancers and signing infrastructure. Certificates expire silently.
When one does, the consequences are immediate and public: browsers reject the site, API clients
fail with handshake errors, email is rejected, and signed builds stop installing. Studies and
industry post-mortems repeatedly show certificate expiry as one of the most common - and most
preventable - causes of unplanned downtime. The root cause is never technical impossibility of
renewal; it is **lack of visibility**: nobody owns the expiry date, nobody sees it approaching,
and nobody gets warned while there is still time.

Existing solutions are either heavyweight (full PKI management platforms), paid SaaS with data
residency concerns, or spreadsheets that go stale the moment they are saved. Small and
mid-sized teams need something simpler: a single place where every certificate is listed, its
remaining lifetime is computed automatically, and the ones that are about to cause an incident
are pushed to the top.

## Goal

Build a **Certificate Expiry Monitor**: a self-hosted web application that

1. stores certificate metadata (name, type, hostname, issuer, owner, environment, issue and
   expiry dates, notes),
2. automatically classifies every certificate as **EXPIRED**, **EXPIRING_SOON** or **VALID**
   using a configurable warning threshold (default 30 days),
3. surfaces the risky ones on a dashboard and an "exceptions" view,
4. lets a team manage the inventory through validated create/edit/delete flows with search,
   filtering, sorting and pagination,
5. ships with demo data so the value is visible within seconds of first launch, and
6. can be built, tested, containerised and deployed automatically (Maven, JUnit, Selenium,
   Jenkins, Docker, Ansible).

## Why this shape of solution

- **Web UI** - the audience is ops/QA people who will not read a database table.
- **Server-rendered (Thymeleaf) SPA-free UI** - simple to test end-to-end with Selenium and
  to operate behind any reverse proxy.
- **Self-hosted, file-based database by default (H2) with a PostgreSQL option** - zero
  infrastructure to try it, a production path when needed.
- **Automated demo data** - evaluators see the dashboard working immediately; demos never
  depend on hand-crafted input.
- **Full CI/CD story** - the project doubles as a realistic DevOps exercise (unit tests,
  browser tests, container build, pipeline, provisioning).

## Out of scope (v1)

- Discovering certificates automatically from servers (scanning/agent).
- Private key storage or CSR generation - the monitor tracks **metadata only**, never keys.
- Certificate authority integrations or automatic renewal (ACME).
- Authentication/authorisation and multi-tenancy.
- Alert delivery channels (email/Slack/webhooks); alerts are presented in the UI.

These are deliberate scope cuts: v1 must be complete, tested and deployable rather than
partially feature-rich.

## Success criteria

| # | Criterion | How it is verified |
|---|-----------|--------------------|
| 1 | Dashboard shows correct counts for the demo dataset (10 total, 2 expired, 4 expiring soon, 4 valid) | MockMvc + Selenium journeys |
| 2 | Status boundaries are exact (expiry today = expiring soon; threshold+1 = valid) | `ExpiryStatusCalculatorTest` |
| 3 | Full CRUD with validation, search, filters, sort, pagination | 37 unit/integration tests |
| 4 | A non-technical user can find a risky certificate in under 3 clicks | Selenium journeys, demo script |
| 5 | One-command local start (`mvnw spring-boot:run` or `docker compose up`) | Verified on the dev machine |
| 6 | Automated pipeline builds, tests, packages and image-builds the app | Jenkinsfile + local verification of each command |
| 7 | Server can be provisioned and the app deployed repeatably | Ansible playbooks (`docs/18-ansible-provisioning.md`) |
