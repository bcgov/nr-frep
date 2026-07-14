# nr-frep documentation

Project documentation for **FREP** (Forest and Range Evaluation Program). These docs are
versioned alongside the code — update them in the same PR as the change they describe.

> For contribution rules see [`../CONTRIBUTING.md`](../CONTRIBUTING.md); for security reporting see
> [`../SECURITY.md`](../SECURITY.md). Root-level `*.local.md` files are intentionally git-ignored
> working notes and are **not** part of this published documentation set.

## Contents

| Doc | What it covers |
|---|---|
| [Architecture](./architecture.md) | System overview, the new app vs. the shared legacy app, key components |
| [Local development](./local-development.md) | Prerequisites and how to run backend + frontend locally |
| [Deployment](./deployment.md) | OpenShift per-PR slots, environments, and cross-repo release coordination |
| [Database](./database.md) | Oracle `THE` schema, the `FREP_*` stored-proc pattern, and `nr-mof-db` migrations |
| [Testing](./testing.md) | Unit tests and Playwright E2E (auth setup, target URLs) |

## Component READMEs

- Backend (Spring Boot): [`../backend/README.md`](../backend/README.md)
- Frontend (React + Vite): [`../frontend/README.md`](../frontend/README.md)

## Related repositories

- **`nr-mof-db`** — Oracle schema + stored procedures (Flyway-versioned). All FREP DB changes land here.
- **`nr-frep-legacy`** — the legacy app sharing the same Oracle database.

<!-- TODO: link the migration-plan / team-status docs here once they move under docs/. -->
