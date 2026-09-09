# Testing

FREP has three test layers: backend unit/slice tests (JUnit), frontend unit/component tests (Vitest),
and end-to-end tests (Playwright) that drive a **deployed** app.

## Backend unit tests

```bash
cd backend
mvn test
```

- Slice/unit tests run against in-memory **H2 in Oracle-compat mode**
  (`src/test/resources/application.properties` → `jdbc:h2:mem:testdb;MODE=Oracle`) — no Oracle needed.
- This means H2 exercises the Java repository/mapping code, **not** the real Oracle stored procedures.
  Proc behavior itself is only verifiable on a deployed DB (see [database.md](./database.md)).

## Frontend unit / component tests

Vitest with two projects — `node` (unit) and `browser` (component/DOM):

```bash
cd frontend
npm test                 # watch mode (all projects)
npm run test:unit        # node project only
npm run test:browser     # browser project (installs Playwright browsers first)
npm run test:ci          # single run, used in CI
npm run test:coverage    # single run with coverage
```

## End-to-end (Playwright)

Config: `frontend/playwright.config.ts`. Specs: `frontend/e2e/*.spec.ts`. Shared helpers:
`frontend/e2e/utils.ts`.

### How it runs

> **Not run in CI as of 2026-09-09.** Both call sites in `pr-open.yml` and `merge.yml` are commented
> out: the CSS integration brokers IDIR - MFA, and a second factor is by construction something the
> CI credentials cannot supply, so `auth.setup.ts` never gets a session and every spec fails at setup
> rather than on what it tests. Re-enabling needs an MFA-exempt service account or a strategy that
> avoids the browser login. **Run it by hand against a deployed environment instead** — the suite is
> kept current. Unit and browser-mode tests still run in CI via `analysis.yml`.

- Tests target a **deployed** app via `E2E_BASE_URL` (defaults to DEV). `utils.ts` throws if
  `E2E_BASE_URL` is unset.
- **Serial execution** (`workers: 1`): all tests share one refresh token, and Keycloak **rotates**
  it on every renewal — parallel workers race that rotation and the loser is left holding a spent
  token, which surfaces as a context stuck on the loading overlay.
- **Two state files, not one.** Playwright's `storageState` captures cookies + localStorage;
  `oidc-client-ts` keeps its tokens in **sessionStorage**, which `storageState` does not touch. The
  sessionStorage half is saved separately and restored by `e2e/fixtures.ts` through an overridden
  `page` fixture — so specs must `import { test } from './fixtures'`, not from `@playwright/test`.

### Auth setup

```bash
cd frontend
npm run e2e:login        # headed: sign in with IDIR once; saves e2e/.auth/user.json
E2E_BASE_URL=<url> npm run e2e   # runs the chromium project, reusing that storageState
```

The `setup` project (`auth.setup.ts`) performs the IDIR login and saves `storageState`; every other
test boots already authenticated.

### Conventions

- Tests are deliberately **data-agnostic** — they assert a page settles into a valid state (no
  `Global Error` boundary) rather than asserting on specific rows, because the E2E target is a
  **shared** database with **no seeded fixtures**.
- Helpers: `gotoProtected(page, path)` (waits past auth bootstrap), `waitForSettled(page, prefix)`
  (waits for the `${prefix}-loading` skeleton to clear), `expectNoGlobalError(page)`.
- **Record-level flows that need addressable, seeded data** (e.g. "open a specific historical record
  and assert read-only") are parked as `test.fixme` and verified manually on DEV/TEST. There is no
  data-seeding mechanism in CI (no DB credentials in the test job); see [database.md](./database.md)
  for why and the options considered.

## CI

- On a PR, the workflow chain builds → deploys to a slot → runs the Playwright chromium project
  against that slot. On merge to `main`, tests run against TEST. See [deployment.md](./deployment.md).

## Related

- [Local development](./local-development.md)
- [Deployment](./deployment.md)
- [Database](./database.md)
