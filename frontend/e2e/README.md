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
lands on `/home`, the session is saved to `e2e/.auth/user.json` (gitignored).

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

All **read-only** — they navigate, assert rendered UI, and exercise only non-destructive actions
(filters, Search/Refresh/Clear, modal open/close, navigation). They never Save/Delete/Submit, so
they're safe against the shared DEV backend.

- **`smoke.spec.ts`** — the app root returns HTTP 200.
- **`navigation.spec.ts`** — each top-level protected screen (Home, Accepted Sites, Checklist
  Search, District Random List, Exports) boots past the auth/loading overlay and renders its `<h1>`,
  without tripping the global error boundary.
- **`dashboard.spec.ts`** — the Home page renders its screen tiles; clicking a tile routes there.
- **`accepted-sites.spec.ts`** — filters + Refresh render, changing the Protocol filter re-loads.
- **`add-target-site.spec.ts`** — the opening-search form renders every filter; Clear resets a field;
  the client-lookup modal opens/closes; Back navigates away.
- **`checklist-search.spec.ts`** — filters render; Search settles; Clear resets; client lookup
  opens/closes.
- **`random-list.spec.ts`** — filters render and the list settles.
- **`reports.spec.ts`** — the Exports page renders.
- **`admin-and-offline.spec.ts`** — the CHR offline list renders; the (role-gated) Generate Master
  List route loads without the error boundary (Generate is never clicked — it mutates data).
- **`detail-resilience.spec.ts`** — site-detail / protocol-checklist / CHR-checklist with a bogus id
  render a graceful in-page state, not the global error boundary.
- **`not-found.spec.ts`** — an unknown route renders the "Not Found" page, not the error boundary.

Shared helpers live in `utils.ts` (`gotoProtected`, `expectNoGlobalError`, `waitForSettled`).
