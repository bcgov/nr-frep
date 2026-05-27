# FREP Backend

Spring Boot backend scaffold for the FREP application. Runs as an OAuth 2.0
resource server validating AWS Cognito access tokens; CSRF is enforced for
state-changing requests via the cookie-token strategy.

## Run locally

Requires **Java 21+**. The project compiles to Java 21 bytecode (`release 21`).

If Maven picks up the wrong JDK (common when Java 26 is the system default), set `JAVA_HOME` first:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
java -version   # should report 21.x
```

```bash
cp .env.example .env
# Fill in AWS_COGNITO_ISSUER_URI and COGNITO_USERINFO_URI from the FAM/Cognito console.
set -a && source .env && set +a
mvn spring-boot:run
```

Backend starts on **http://localhost:8080**.

```bash
curl http://localhost:8080/api/hello
# "Hello World" — public endpoint, no auth required.

curl http://localhost:8080/api/v1/accepted-sites?effectiveYear=2024&orgUnit=56
# 401 Unauthorized — Cognito bearer token required.
```

## Required environment variables

| Variable | Description |
|---|---|
| `AWS_COGNITO_ISSUER_URI` | Cognito user-pool URL (`https://cognito-idp.<region>.amazonaws.com/<pool-id>`). |
| `COGNITO_USERINFO_URI` | Cognito `/oauth2/userInfo` endpoint URL. |
| `ALLOWED_ORIGINS` | Comma-separated list of allowed CORS origins. Defaults to `http://localhost:3000`. |
| `IDENTITY_LOOKUP_BASE_URL` | Optional: identity lookup service base URL. Leave blank to disable. |

See `.env.example` for the full list.

## Authorization model

`ApiAuthorizationCustomizer` enforces the following rules on top of
`SecurityConfiguration`:

| Path                | Required authorities |
|---|---|
| `OPTIONS /**`       | (preflight, always permitted) |
| `/actuator/**`      | public (for OpenShift probes + Prometheus) |
| `/api/hello`        | public |
| `/api/v1/admin/**`  | `FREP_SYS_ADMIN` |
| `GET /api/**`       | any of `FREP_SYS_ADMIN`, `FREP_UPDATE`, `FREP_VIEW_ONLY` |
| `POST/PUT/PATCH/DELETE /api/**` | `FREP_SYS_ADMIN` or `FREP_UPDATE` |
| anything else       | authenticated |
