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
| `/api/v1/admin/**`  | `FREP_ADMIN` |
| `GET /api/**`       | any of `FREP_ADMIN`, `FREP_EDITOR`, `FREP_VIEW_ONLY` |
| `POST/PUT/PATCH/DELETE /api/**` | `FREP_ADMIN` or `FREP_EDITOR` |
| anything else       | authenticated |

## Oracle connectivity (legacy schema)

Oracle is configured via standard Spring Boot {@code spring.datasource.*}
properties (same pattern as {@code nr-fspts}). The JDBC URL is composed from
{@code DATABASE_HOST} + {@code DATABASE_SERVICE_NAME} (TCPS on port 1543;
truststore defaults to {@code /cert/jssecacerts} in-cluster).

```bash
cp .env.example .env
# Set DATABASE_* (and Cognito) in .env, or use application-local.yml for compose
set -a && source .env && set +a
mvn spring-boot:run
```

Verify Oracle connectivity via the authenticated configuration API:

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/configuration/org-units
```

District rows are loaded via legacy
`FREP_CODE_LISTS.GET_DISTRICT_ORG_UNIT_CODE`. Master-list years are loaded via
`FREP_CODE_LISTS.GET_MASTERLIST_YEAR_CODE`. Protocols are loaded via
`FREP_CODE_LISTS.GET_RESOURCE_VALUE`. Accepted sites are loaded via
`FREP_200_ACCEPTED_SITES.GET`. District random list sites are loaded via
`FREP_100_DIST_RAND_LIST.GET`. Checklist and client search use
`FREP_400_CHECKLIST_SEARCH` and `FREP_410_CLIENT_SEARCH`. Site details are loaded via
`FREP_110_SITE_DETAILS.GET`. Master list admin criteria and generation use
`FREP_700_GEN_MASTER.GET` and `FREP_700_GEN_MASTER.GENERATE`. Protocol checklists
are loaded via `frep_210_bio_opening.GET`, `FREP_211_BioStratum.get`,
`FREP_212_BioPlot.get`, `FREP_230_STRM_OPEN.GET`, `FREP_231_FIELD_DATA.GET`,
`FREP_232_OTHER_INDS.GET`, `FREP_233_QUESTIONS.GET`, `FREP_234_SPECIFIC_IMPACTS.GET`,
`FREP_235_FINAL_CMTS.GET`, `FREP_250_WATER_CHKLST_GET`, `FREP_251_SAMPLE_SITE_GET`,
`FREP_252_ASSESSMENT_GET`, `FREP_253_RANGE_GET`, and `FREP_254_SUMMARY_GET`.

| Variable | Description |
|---|---|
| `DATABASE_HOST` | Oracle listener host |
| `DATABASE_PORT` | Listener port (default `1543` for TCPS) |
| `DATABASE_SERVICE_NAME` | Oracle service name |
| `DATABASE_USER` / `DATABASE_PASSWORD` | FREP schema credentials |
| `TRUSTSTORE_PATH` | Local path to TCPS truststore (cluster default: `/cert/jssecacerts`) |
| `KEYSTORE_SECRET` | Truststore password |
