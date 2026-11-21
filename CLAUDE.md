# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

A reference shop (products, stock, orders) built as Spring Boot microservices: Java 27, Spring Boot 4.1.1, Spring Cloud 2025.1.3, Maven wrapper. `README.md` is the full user guide (API table, manual curl walkthrough, Kubernetes troubleshooting); this file covers what you need to change code.

## Commands

```bash
./mvnw verify                                   # build + all tests (JDK 27, no Docker needed)
./mvnw -pl order-service verify                 # one module
./mvnw -pl order-service test -Dtest=OrderApiTests            # one test class
./mvnw -pl order-service test -Dtest='OrderApiTests#methodName'  # one test method

docker compose up --build -d && ./scripts/smoke-test.sh   # whole stack + end-to-end check (needs curl, jq)
docker compose down [-v]                                  # -v also drops the Postgres volume

./scripts/k8s-check.sh                          # offline manifest check (needs kubectl, no cluster)
./scripts/k8s-images.sh [registry]              # build (and push) the eight images; PLATFORM=linux/amd64 to cross-build
kubectl apply -k .                              # deploy k8s/ via root kustomization.yaml
```

There is no linter or formatter configured. Code uses tabs for indentation.

## Architecture

Eight Maven modules under one parent `pom.xml`, package root `com.vanmanh49.<module>`:
`discovery-server` (Eureka, 8761), `config-server` (8888), `api-gateway` (8080, the only public entry), `auth-service` (8081), `product-service` (8082), `inventory-service` (8083), `order-service` (8084), `notification-service` (8085).

**Configuration lives in the config server, not the services.** Each service's `src/main/resources/application.yml` only names the app and imports `configserver:`. Real settings are in `config-server/src/main/resources/configs/<service>.yml`, with shared settings (tracing, metrics) in `configs/application.yml`. Edit those files when changing service behaviour/ports/DB/Kafka settings.

**Auth model.** `auth-service` issues JWTs and publishes the public key via JWKS (`JwksController`); the key is regenerated on every start. The gateway validates tokens (`SecurityConfig`), then forwards `X-User-Id` / `X-User-Roles` headers (`IdentityHeaders`). Downstream services trust these headers and never validate tokens themselves. `SecurityConfig` rules are checked top to bottom and the first match wins. A new endpoint takes the rule of its prefix: `GET /api/products/**` is public, other methods there and `PUT /api/inventory/**` are admin-only, and the rest of `/api/**` needs a signed-in user. A new service prefix also needs a route in `RouteConfig`. Outside `/api`, only `/fallback`, `/actuator/health/**` and `/actuator/prometheus` are open. Internal-only endpoints (e.g. `/api/inventory/reservations/**`) are explicitly denied at the gateway.

**Order saga** (`order-service` → `OrderService.place`): fetch products from `product-service` → reserve stock in `inventory-service` (all-or-nothing, 409 if short) → save order; on save failure, release the reservation (compensation). After commit, `OrderStore`/`OrderEventRelay`/`OrderEventPublisher` publish `ORDER_PLACED`/`ORDER_CANCELLED` to Kafka topic `order-events`. `notification-service` consumes them idempotently (`NotificationService.handle`) and skips bad messages (`OrderEventListener` + consumer settings in `configs/notification-service.yml`).

**Inter-service calls** use load-balanced HTTP clients by Eureka name (`http://product-service`), configured in `order-service/.../client/ClientConfig`, with Resilience4j circuit breakers. The gateway also has a circuit-breaker fallback (`FallbackController`).

**Overselling prevention** is in `inventory-service`: atomic `InventoryItemRepository.decrement`, plus one `reservation_orders` row per order that reserve and release both write first (`ReservationOrderRepository`), so a release that overtakes a slow reservation still returns its stock and a released order cannot reserve again. The release side uses Postgres `insert ... on conflict do nothing`; there are dedicated concurrency tests.

**Per-service conventions:**
- Each service owns its PostgreSQL database; schema is Flyway-only (`src/main/resources/db/migration/V<n>__*.sql`), JPA runs with `ddl-auto: validate`. Add a new migration rather than editing an existing one. The per-service databases are created by `infra/postgres/init.sql`, which Compose mounts and the root `kustomization.yaml` also turns into the `postgres-init` ConfigMap for Kubernetes.
- Errors are RFC 9457 problem details, mapped in each service's `web/ApiExceptionHandler`.
- DTOs and the order event are deliberately **duplicated** across services (no shared module); services depend only on each other's JSON. Keep it that way, and update both sides when a contract changes.

## Tests

`@SpringBootTest` + MockMvc against in-memory H2 (`MODE=PostgreSQL`) running the real Flyway migrations. Each module's `src/test/resources/application.yml` disables config server, Eureka and trace export; other services and Kafka are replaced with `@MockitoBean`, except `OrderEventListenerIntegrationTests`, which uses `@EmbeddedKafka` (in-process, still no Docker). Order tests are intentionally not `@Transactional` because events publish only after a real commit.

## Deployment

- `Dockerfile` is shared: one build stage compiles all modules, `--build-arg MODULE=<name>` picks the jar. `docker-compose.yml` runs 12 containers (~4 GB) and holds the dev credentials (admin `admin`/`admin12345`).
- `k8s/` holds plain manifests assembled by the root `kustomization.yaml` (namespace `shop`). `scripts/k8s-check.sh` asserts exact resource counts, probe settings (`timeoutSeconds: 5` on all probes, startup probe `failureThreshold: 30`/`periodSeconds: 10`) and Kafka's `enableServiceLinks: false`. Update its expectations when adding or removing manifests/probes.

## Design docs

Specs and implementation plans are in `docs/superpowers/specs/` and `docs/superpowers/plans/`. The README section "Shortcuts taken" lists known, intentional gaps (no outbox, header trust, regenerated signing key); don't "fix" them silently.
