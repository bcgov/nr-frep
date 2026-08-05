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

- Tests target a **deployed** app via `E2E_BASE_URL` (defaults to DEV; a per-PR slot in CI — see
  [deployment.md](./deployment.md)). `utils.ts` throws if `E2E_BASE_URL` is unset.
- **Serial execution** (`workers: 1`): all tests share one Cognito refresh token via `storageState`;
  parallel workers race that refresh and get stuck on the loading overlay.

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
