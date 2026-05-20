# FREP

A full-stack application scaffold for the Natural Resources sector.

| Component | Technology |
|-----------|------------|
| Frontend | React 19, TypeScript, Carbon Design System |
| Backend | Spring Boot 3.5, Java 21 |
| Database | Oracle (optional `oracle` profile) |
| Auth | AWS Cognito (FAM) — **disabled for local dev** |

## Run locally (no auth)

Cognito and IDIR login are commented out temporarily in the backend and frontend.

### Prerequisites

- Java 21+ and Maven 3.9+
- Node.js 20+ and npm

### 1. Start the backend

```bash
cd backend
mvn spring-boot:run
```

Backend listens on **http://localhost:8080**.

### 2. Start the frontend

In a second terminal:

```bash
cd frontend
npm run dev
```

Open **http://localhost:3000**.

### Port layout

| Service | URL |
|---------|-----|
| Frontend (Vite) | http://localhost:3000 |
| Backend (Spring Boot) | http://localhost:8080 |

Leave `VITE_BACKEND_URL` empty in `frontend/.env` so Vite proxies `/api` to the backend.

[//]: # (### Re-enabling Cognito / IDIR)

[//]: # ()
[//]: # (Before deploying, uncomment the auth code marked `LOCAL DEV` or `re-enable before deploying` in:)

[//]: # ()
[//]: # (- `frontend/src/main.tsx`)

[//]: # (- `frontend/src/context/auth/AuthProvider.tsx`)

[//]: # (- `frontend/src/config/fam/config.ts`)

[//]: # (- `backend/src/main/resources/application.yml`)

[//]: # (- `backend/.../SecurityConfiguration.java`)

[//]: # (- `backend/.../ApiAuthorizationCustomizer.java`)

[//]: # (- `backend/.../Oauth2SecurityCustomizer.java` &#40;`@Component`&#41;)

[//]: # (- `backend/.../CognitoUserInfoService.java` &#40;`@Service`&#41;)

[//]: # (- `backend/.../LoggedUserHelper.java` &#40;`@Component`&#41;)

[//]: # ()
[//]: # (Then restore Cognito values in both `.env` files.)

[//]: # ()
[//]: # (## What's included)

[//]: # ()
[//]: # (- **Frontend:** Landing page, dashboard, layout, routing &#40;mock local user&#41;)

[//]: # (- **Backend:** Spring Boot app, actuator health, `GET /api/hello`)

[//]: # (- **CI/CD:** GitHub Actions workflows, compliance files, deployment templates)
