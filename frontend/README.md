# FREP Frontend

React frontend scaffold for the FREP application.

## Run locally (no auth)

```bash
cp .env.example .env
npm ci
npm run dev
```

Open **http://localhost:3000** and click **Go to dashboard**. Cognito / IDIR login is disabled; a mock local user is injected automatically.

Ensure the backend is running on port **8080** and `VITE_BACKEND_URL` is left empty so `/api` is proxied by Vite.

## Re-enabling Cognito / IDIR

Uncomment Amplify setup in `src/main.tsx`, restore the Cognito `AuthProvider` implementation, and set `VITE_USER_POOLS_ID` / `VITE_USER_POOLS_WEB_CLIENT_ID` in `.env`.
