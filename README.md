# FREP

A full-stack application scaffold for the Natural Resources sector.

| Component | Technology |
|-----------|------------|
| Frontend | React 19, TypeScript, Carbon Design System |
| Backend | Spring Boot 3.5, Java 21 |
| Database | Oracle (optional `oracle` profile) |
| Auth | AWS Cognito (FAM) — IDIR sign-in |

## Run locally

Authentication runs against the real FAM Cognito user pool; you'll need
Cognito client / domain values in both `.env` files before the app will
fully boot. The frontend will render the landing page without them and the
backend will respond `401` to `/api/**` until you sign in.

### Prerequisites

- Java 21+ and Maven 3.9+
- Node.js 20+ and npm
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

## What's included

- **Frontend:** Landing, dashboard, layout, routing, role-aware navigation, full Cognito + IDIR auth flow.
- **Backend:** Spring Boot OAuth 2.0 resource server, CSRF cookie strategy, `/api/v1/*` endpoints, actuator health.
- **CI/CD:** GitHub Actions workflows, compliance files, deployment templates.
