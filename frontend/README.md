# FREP Frontend

React frontend scaffold for the FREP application.

## Run locally

```bash
cp .env.example .env
npm ci
npm run dev
```

Ensure the backend is running on port **8080** and `VITE_BACKEND_URL` is left empty so `/api` is
proxied by Vite.

## Auth

Sign-in goes through BC Gov SSO (Keycloak, standard realm) using `oidc-client-ts` — Authorization
Code + PKCE, public client, tokens in `sessionStorage`. There is no auth-off mode: the landing page
renders signed out, and everything past it needs a real session.

Two values configure the whole thing, in `.env`:

| Variable | Value |
|---|---|
| `VITE_KEYCLOAK_URL` | the realm issuer URI, `https://<env>.loginproxy.gov.bc.ca/auth/realms/standard` |
| `VITE_KEYCLOAK_CLIENT_ID` | the CSS integration's client id |

Everything else — the authorize, token, JWKS and end-session endpoints — is discovered from the
issuer's `.well-known/openid-configuration`. If you are reaching for an endpoint variable, it is
already in the discovery document.

The redirect URIs are **derived from `window.location.origin`**, not configured, which is what lets
one built image serve PR previews, TEST and PROD. For local dev that means
`http://localhost:3000/authCallback` (sign-in) and `http://localhost:3000` (post-logout) have to be
registered on the CSS integration.

The backend reads the same two values as `KEYCLOAK_ISSUER_URI` / `KEYCLOAK_CLIENT_ID`. The names
differ; the values must not.
