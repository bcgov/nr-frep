# FREP Backend

Spring Boot backend scaffold for the FREP application.

## Run locally (no auth)

Requires **Java 21+**. The project compiles to Java 21 bytecode (`release 21`).

If Maven picks up the wrong JDK (common when Java 26 is the system default), set `JAVA_HOME` first:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
java -version   # should report 21.x
```

```bash
cp .env.example .env
set -a && source .env && set +a
mvn spring-boot:run
```

Backend starts on **http://localhost:8080**. Cognito JWT validation is disabled for local development.

```bash
curl http://localhost:8080/api/hello
# Hello World
```

## Re-enabling Cognito

Uncomment the auth-related blocks in `application.yml`, `SecurityConfiguration.java`, `ApiAuthorizationCustomizer.java`, and restore `@Component` / `@Service` on the Cognito security beans. Set `AWS_COGNITO_ISSUER_URI` and related env vars before starting.
