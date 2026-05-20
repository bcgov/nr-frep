# FREP

A full-stack application scaffold for the Natural Resources sector.

| Component | Technology |
|-----------|------------|
| Frontend | React 19, TypeScript, Carbon Design System |
| Backend | Spring Boot 3.5, Java 21 |
| Database | Oracle (optional `oracle` profile) |
| Auth | AWS Cognito (FAM) — **disabled for local dev** |

## Run locally (no auth)

Cognito and IDIR login are commented out so you can run the stack without credentials.

### Prerequisites

- Java 21+ and Maven 3.9+
- Node.js 20+ and npm

If `mvn compile` fails with `TypeTag :: UNKNOWN`, Maven is using an JDK that is too new for the pinned Lombok version, or an old Lombok is cached. Use Java 21+ and run a clean build:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
cd backend && mvn clean compile
```

### 1. Start the backend

```bash
cd backend
cp .env.example .env
set -a && source .env && set +a   # Windows: set vars from .env manually
mvn spring-boot:run
```

Backend listens on **http://localhost:8080**.

Verify:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/hello
```

Both should succeed without a token.

### 2. Start the frontend

In a second terminal:

```bash
cd frontend
cp .env.example .env
npm ci
npm run dev
```

Open **http://localhost:3000**, click **Go to dashboard**, and you should see **Hello World** from `GET /api/hello`.

### Port layout

| Service | URL |
|---------|-----|
| Frontend (Vite) | http://localhost:3000 |
| Backend (Spring Boot) | http://localhost:8080 |
| API via Vite proxy | http://localhost:3000/api/hello |

Leave `VITE_BACKEND_URL` empty in `frontend/.env` so Vite proxies `/api` to the backend.

### Re-enabling Cognito / IDIR

Before deploying, uncomment the auth code marked `LOCAL DEV` or `re-enable before deploying` in:

- `frontend/src/main.tsx`
- `frontend/src/context/auth/AuthProvider.tsx`
- `frontend/src/config/fam/config.ts`
- `backend/src/main/resources/application.yml`
- `backend/.../SecurityConfiguration.java`
- `backend/.../ApiAuthorizationCustomizer.java`
- `backend/.../Oauth2SecurityCustomizer.java` (`@Component`)
- `backend/.../CognitoUserInfoService.java` (`@Service`)
- `backend/.../LoggedUserHelper.java` (`@Component`)

Then restore Cognito values in both `.env` files.

## What's included

- **Frontend:** Landing page, dashboard, layout, routing (mock local user)
- **Backend:** Spring Boot app, actuator health, `GET /api/hello`
- **CI/CD:** GitHub Actions workflows, compliance files, deployment templates
