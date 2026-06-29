# Playwright E2E

These tests hit a real FREP backend. They default to the deployed **DEV**
environment; override with `E2E_BASE_URL=...` to point at local dev or another deployed env.

## One-time auth bootstrap

Cognito + BC Gov IDIR can't be scripted headlessly. Run the setup project
once interactively to capture an authenticated session:

```bash
npm run e2e:login
```

A Chromium window opens, navigates to the landing page, and clicks **Log in
with IDIR**. Finish the IDIR sign-in (and any MFA) by hand. Once the app
lands on `/dashboard`, the session is saved to `e2e/.auth/user.json` (gitignored).

Re-run `npm run e2e:login` whenever the saved session expires — you'll know
because tests start bouncing back to the IDIR domain or seeing 401s.

## Running the suite

```bash
npm run e2e            # chromium only (default)
npm run e2e:all-browsers   # chromium + chrome + firefox + safari + edge
npm run e2e:ui         # Playwright's interactive UI
npm run e2e:report     # open the last HTML report
```

All non-setup projects depend on `setup`, so Playwright will yell if you
haven't run the login bootstrap yet.

## What's covered

All read-only — they navigate and assert rendered UI, never writing to the backend.

- **`smoke.spec.ts`** — the app root returns HTTP 200.
- **`navigation.spec.ts`** — each top-level protected screen (Dashboard, Accepted Sites, Checklist
  Search, District Random List, Reports) boots past the auth/loading overlay and renders its own
  `<h1>`, without tripping the global error boundary.
- **`dashboard.spec.ts`** — the Dashboard renders its screen tiles, and clicking a tile routes to
  that screen.
- **`not-found.spec.ts`** — an unknown route renders the graceful "Not Found" page rather than the
  global error boundary.

Shared helpers live in `utils.ts` (`gotoProtected`, `expectNoGlobalError`).
