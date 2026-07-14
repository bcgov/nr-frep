# Local development

How to run FREP on your machine. The app has **no local database** — it connects to the shared
Oracle over the BC Gov VPN — and authentication runs against the real FAM/Cognito user pool.

## Prerequisites

- **Java 21+** and **Maven 3.9+** (the backend compiles to Java 21 bytecode).
- **Node.js 20+** and **npm** (Compose dev uses Node 22; CI and the production image use Node 24).
- **BC Gov VPN** connected — required for the backend to reach the Oracle host; Compose/your machine
  can't route to it otherwise.
- **Cognito / FAM values** for auth (see `frontend/.env.example` and `backend/.env.example`), plus
  Oracle credentials and the TCPS truststore for DB access.

> If Maven picks up the wrong JDK (common when a newer JDK is the system default):
> ```bash
> export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
> java -version   # should report 21.x
> ```

## Backend

```bash
cd backend
cp .env.example .env
# Fill in AWS_COGNITO_ISSUER_URI, COGNITO_USERINFO_URI, and DATABASE_* values.
set -a && source .env && set +a
mvn spring-boot:run
```

- Listens on **http://localhost:8080**.
- `GET /api/hello` is public ("Hello World"); every `/api/v1/**` route returns **401** without a
  Cognito bearer token.
- Oracle is wired via `spring.datasource.*` — the JDBC URL is composed from `DATABASE_HOST` +
  `DATABASE_SERVICE_NAME` (TCPS on port `1543`; truststore defaults to `/cert/jssecacerts` in-cluster).
  See [database.md](./database.md) for the connectivity variables.

Full env var list and the authorization model are in [`../backend/README.md`](../backend/README.md).

## Frontend

```bash
cd frontend
cp .env.example .env
npm ci
npm run dev
```

- Open **http://localhost:3000**.
- Leave `VITE_BACKEND_URL` **empty** so Vite proxies `/api` to the backend on `:8080`.
- **Auth-off mode:** by default local dev injects a mock user (no Cognito). Click **Go to dashboard**.
- **Auth-on mode:** to test the real IDIR flow, re-enable Amplify/Cognito per
  [`../frontend/README.md`](../frontend/README.md) and set `VITE_USER_POOLS_ID` /
  `VITE_USER_POOLS_WEB_CLIENT_ID`. The **Log in with IDIR** button routes Cognito → BC Gov SSO →
  `/dashboard`.

## Running with Docker Compose

`compose.yml` runs the backend (`:8080`) and Vite frontend (`:3000`); there is **no database
service** — it still uses the shared Oracle, so the VPN must be up.

```bash
docker compose up                        # backend + frontend
docker compose --profile caddy up caddy  # prod-like Caddy + WAF image on :3005
```

Prerequisites the Compose stack expects on the host:
1. BC Gov VPN connected.
2. `backend/src/main/resources/application-local.yml` (gitignored) — DB password + Cognito/IDIR config
   read by the `local` Spring profile.
3. `backend/src/main/resources/cert/jssecacerts` — the Oracle TCPS truststore (copy from a running pod;
   procedure is in the `application-local.yml` header).
4. `frontend/.env` — filled from `.env.example`.

Notes:
- **Backend has no hot reload** (no devtools) — `docker compose restart backend` after Java changes.
  The frontend hot-reloads natively.
- First `up` downloads the Maven cache (~5 min); the `maven-cache` volume persists it.

## Database access during development

There is no local Oracle. To develop or verify DB-touching changes:
- **Unit/slice tests** run against in-memory **H2 in Oracle-compat mode** (no DB needed) — see
  [testing.md](./testing.md).
- **Real proc/SQL behavior** must be verified on **DEV/TEST** after deploy — schema and stored
  procedures live in the separate **`nr-mof-db`** repo. See [database.md](./database.md).

## Related

- [Testing](./testing.md)
- [Database](./database.md)
- [Deployment](./deployment.md)
