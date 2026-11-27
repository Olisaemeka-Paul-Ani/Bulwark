# Bulwark

An API security gateway built with Java 21 and Spring Cloud Gateway. It sits in front of backend
services, rate-limits clients with a Redis token bucket, scores each client's behaviour with an
Isolation Forest, and quarantines bad clients across every gateway node using Postgres
LISTEN/NOTIFY.

> Work in progress. Right now the gateway only routes traffic to the demo services.

## Modules

| Module | What it is |
|---|---|
| `common` | Shared DTOs (`RequestEvent`) |
| `gateway` | Spring Cloud Gateway (WebFlux) |
| `demo-orders` | Dummy orders backend (`/orders`) |
| `demo-users` | Dummy users backend (`/users`) |
| `infra/` | Docker Compose stack |

## Requirements

- Java 21
- Docker with Compose v2

## Build and test

```sh
./mvnw verify
```

## Run locally

```sh
docker compose -f infra/docker-compose.yml up --build
```

This starts Postgres, Redis, both demo services and the gateway. The gateway listens on
`localhost:8080`:

```sh
curl localhost:8080/orders
curl localhost:8080/orders/1
curl localhost:8080/users/42
```

Stop everything with:

```sh
docker compose -f infra/docker-compose.yml down
```

Postgres credentials default to local-only dev values; override them with `POSTGRES_USER`,
`POSTGRES_PASSWORD` and `POSTGRES_DB`.
