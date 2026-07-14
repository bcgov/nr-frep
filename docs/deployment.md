# Deployment

FREP deploys to **OpenShift** (BC Gov Gold cluster) via GitHub Actions. The **app** is deployed
per-PR into numbered slots; the **database is a single shared external Oracle**, not per-PR.

## Environments

- Cluster domain: `apps.gold.devops.gov.bc.ca`
- Per-PR app URL: `https://nr-frep-<slot>.apps.gold.devops.gov.bc.ca`
- **TEST**: `https://nr-frep-test.apps.gold.devops.gov.bc.ca` (deployed on merge to `main`)
- **PROD**: <!-- TODO: confirm prod URL / promotion step -->

## Per-PR slots

- Each PR deploys to an app slot computed as **`slot = PR# % 50`**. The cap of 50 exists because AWS
  Cognito has 50 pre-registered callback URIs for the IDIR redirect.
- Only the **application pods** (backend + frontend) are per-PR. The per-PR **database was
  deliberately dropped** — all slots point at the **same shared DEV Oracle**.
- **Implication:** any data created on any slot (via the UI/API or a test) lands in the shared DEV
  Oracle, is visible to every other slot, and **persists** (no teardown). Tests are written to be
  data-agnostic for this reason — see [testing.md](./testing.md).

## CI/CD workflows

Located in `.github/workflows/`:

- **`pr-open.yml`** (on `pull_request`) → build → `reusable-deploy.yml` (deploy to the PR slot) →
  `reusable-tests.yml` (Playwright E2E against the slot).
- **`merge.yml`** (on merge to `main`) → deploy + run the same tests against **TEST**.
- **`reusable-deploy.yml`** — the shared deploy job; injects Oracle + Cognito + object-storage secrets
  into the pods. (Note: DB credentials go to the **app pod only**, never to the test job.)
- **`reusable-tests.yml`** — the shared E2E job; receives only `E2E_IDIR_USER` / `E2E_IDIR_PASSWORD`.
  It resolves `E2E_BASE_URL` to the slot/target URL and runs `npx playwright test`.

## Cross-repo release coordination

FREP spans three repos that share one Oracle database:

- **`nr-frep`** (this repo) — the new app.
- **`nr-mof-db`** — Oracle schema + stored procedures (Flyway). All DB changes ship here.
- **`nr-frep-legacy`** — the legacy app on the same schema.

Rules of thumb:
- **DB before app.** A `nr-mof-db` migration must deploy before app code that depends on it. Keep the
  dependent app PR in **draft** until the DB PR merges/deploys.
- **Stay legacy-compatible.** Because the legacy app hits the same shared procs and data, DB changes
  must not break it (e.g. the SLB→SLR work *added* `SLR` handling alongside `SLB` rather than
  replacing it). See [database.md](./database.md).

## Runtime notes

- The production frontend image is **Caddy + Coraza WAF**, with runtime config seeded by an
  entrypoint script (replicated locally via the `caddy` Compose profile — see
  [local-development.md](./local-development.md)).
- `/actuator/**` is public for OpenShift health probes and Prometheus scraping.

## Related

- [Database](./database.md)
- [Testing](./testing.md)
- [Architecture](./architecture.md)
