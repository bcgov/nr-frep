# FREP - Forest and Range Evaluation Program

FREP is a BC Government application for evaluating forest and range practices. Field evaluators select
forestry sites for a master-list year, capture and edit protocol checklists (biodiversity and Culture
Heritage), search across checklists, and generate reports.

| Component | Technology |
|-----------|------------|
| Frontend | React 19, TypeScript, Carbon Design System |
| Backend | Spring Boot 3.5, Java 21 |
| Database | Oracle (`spring.datasource.*`, same as nr-fspts) |
| Auth | AWS Cognito (FAM) — IDIR sign-in |

## Documentation

Full project documentation lives in [`docs/`](./docs/README.md):

- [Architecture](./docs/architecture.md) — components, data flow, auth
- [Site map](./docs/site-map.md) — pages, routes, and navigation flow
- [Local development](./docs/local-development.md) — full setup guide (Compose, VPN/Oracle, auth modes)
- [Deployment](./docs/deployment.md) — OpenShift slots, CI/CD, cross-repo release coordination
- [Database](./docs/database.md) — Oracle `THE` schema + the stored-procedure pattern
- [Testing](./docs/testing.md) — unit tests + Playwright E2E

## Run locally

> Quickstart below. See [docs/local-development.md](./docs/local-development.md) for the full guide —
> Docker Compose, VPN/Oracle setup, and auth-on vs. auth-off modes.

Authentication runs against the real FAM Cognito user pool; you'll need
Cognito client / domain values in both `.env` files before the app will
fully boot. The frontend will render the landing page without them and the
backend will respond `401` to `/api/**` until you sign in.

### Prerequisites

- Java 21+ and Maven 3.9+
- Node.js 20+ and npm (CI and the production image use Node 24)
- Cognito user pool details (see `frontend/.env.example` and `backend/.env.example`)

### 1. Start the backend

```bash
cd backend
cp .env.example .env       # fill in AWS_COGNITO_ISSUER_URI + COGNITO_USERINFO_URI
set -a && source .env && set +a
mvn spring-boot:run
```

Backend listens on **http://localhost:8080**. Hitting `/api/**` without a
Cognito bearer token returns `401`.

### 2. Start the frontend

In a second terminal:

```bash
cd frontend
cp .env.example .env       # fill in VITE_USER_POOLS_ID + VITE_USER_POOLS_WEB_CLIENT_ID
npm run dev
```

Open **http://localhost:3000**. The landing page's **Log in with IDIR** button
redirects through Cognito → BCGov SSO → back to `/dashboard`.

### Port layout

| Service | URL |
|---------|-----|
| Frontend (Vite) | http://localhost:3000 |
| Backend (Spring Boot) | http://localhost:8080 |

Leave `VITE_BACKEND_URL` empty in `frontend/.env` so Vite proxies `/api` to the backend.

## Features

- **Site selection** — district random lists and targeted-site creation for a master-list year.
- **Protocol checklists** — biodiversity (SLB/SLR) and Culture Heritage (CHR, offline-capable):
  capture, edit, and submit.
- **Search & reports** — cross-protocol checklist search plus Jasper/CSV reporting.
- **Role-aware access** — FAM/IDIR sign-in with `FREP_ADMIN` / `FREP_EDITOR` / `FREP_VIEW_ONLY` roles.

See [docs/architecture.md](./docs/architecture.md) for the full breakdown.
