# Shop Microservices — Design

Date: 2026-10-07
Status: awaiting review

## 1. Purpose

A learning/reference microservices system built with Spring Boot. It demonstrates the
standard patterns — service discovery, centralized configuration, an API gateway, JWT
authentication, database per service, synchronous calls with circuit breakers,
asynchronous events, distributed tracing and metrics — on a small e-commerce domain.

The reader is a developer studying how the pieces fit together. Readability takes priority
over production hardening; every shortcut is named in the README.

### Success criteria

1. `docker compose up --build` starts the whole system. Docker is the only host
   requirement, because the build runs inside the image.
2. `./mvnw verify` passes on the host with JDK 21+ and no Docker running.
3. `scripts/smoke-test.sh` passes against the running stack: register, log in, create a
   product, set stock, place an order, see the notification, cancel the order, see stock
   restored.
4. The README explains the architecture, how to run it, and where each pattern lives.

### Out of scope

Kubernetes manifests, CI pipeline, Grafana dashboards, a frontend, a transactional outbox,
persistent JWT signing keys, refresh tokens, rate limiting, multi-instance deployment
tuning. The README lists these as next steps.

## 2. Technology

| Concern | Choice |
|---|---|
| Language / build | Java 21, Maven multi-module, Maven wrapper |
| Framework | Spring Boot 4.1.1, Spring Cloud 2025.1.3 |
| Discovery | Spring Cloud Netflix Eureka |
| Configuration | Spring Cloud Config Server, native (file) backend |
| Gateway | Spring Cloud Gateway Server WebMVC |
| Resilience | Spring Cloud CircuitBreaker with Resilience4j |
| Persistence | PostgreSQL, Spring Data JPA, Flyway |
| Messaging | Apache Kafka (single node, KRaft), Spring for Apache Kafka, JSON payloads |
| Security | RSA-signed JWTs (Nimbus), Spring Security OAuth2 resource server at the gateway |
| Observability | Actuator, Micrometer, Zipkin tracing, Prometheus metrics |
| Tests | JUnit 5, MockMvc, H2, Mockito |
| Runtime | Docker Compose |

No Lombok: records for DTOs, hand-written entities.

## 3. Modules

Maven group `com.example`, parent artifact `shop-microservices`. Each module's base package
is `com.example.<name>` (`discovery`, `config`, `gateway`, `auth`, `product`, `inventory`,
`order`, `notification`).

| Module | Port | Responsibility | Depends on |
|---|---|---|---|
| `discovery-server` | 8761 | Eureka registry | — |
| `config-server` | 8888 | Serves configuration files bundled on its classpath | — |
| `api-gateway` | 8080 | Routing, JWT validation, identity headers, circuit breaker fallback | config, discovery, auth (JWKS) |
| `auth-service` | 8081 | Registration, login, JWT issuing, JWKS endpoint | config, discovery, Postgres |
| `product-service` | 8082 | Product catalog | config, discovery, Postgres |
| `inventory-service` | 8083 | Stock levels and reservations | config, discovery, Postgres |
| `order-service` | 8084 | Order placement and cancellation, event publishing | config, discovery, Postgres, Kafka, product, inventory |
| `notification-service` | 8085 | Consumes order events, stores notifications | config, discovery, Postgres, Kafka |

Only the gateway (8080), Eureka dashboard (8761), Zipkin (9411) and Prometheus (9090) are
published to the host. Business services are reachable only inside the Compose network.

### Configuration layout

- `config-server/src/main/resources/config/application.yml` — shared settings: Eureka
  client, actuator exposure, tracing, problem details.
- `config-server/src/main/resources/config/<service>.yml` — per-service settings: port,
  datasource, Kafka, resilience.
- Each client's own `application.yml` holds only its name and
  `spring.config.import=configserver:${CONFIG_SERVER_URL:http://localhost:8888}`. The import
  is not optional, so a missing config server fails startup loudly.
- Environment-specific values (hostnames, credentials) come from environment variables with
  localhost defaults, set in `docker-compose.yml`.

## 4. Security model

- `auth-service` generates an RSA key pair at startup, signs access tokens (1 hour) with
  claims `sub` (user id), `username`, `roles`, and serves the public key at
  `/.well-known/jwks.json`. Passwords are stored as BCrypt hashes.
- An admin user is seeded at startup from `ADMIN_USERNAME` / `ADMIN_PASSWORD` (development
  defaults set in Compose and called out in the README).
- `api-gateway` is an OAuth2 resource server using that JWKS URI. Access rules:

  | Path | Rule |
  |---|---|
  | `POST /api/auth/register`, `POST /api/auth/login` | public |
  | `GET /api/products/**` | public |
  | `POST/PUT/DELETE /api/products/**` | role `ADMIN` |
  | `PUT /api/inventory/**` | role `ADMIN` |
  | `GET /api/inventory/**` | authenticated |
  | `/api/inventory/reservations/**` | denied (internal only) |
  | `/api/orders/**`, `/api/notifications/**`, `GET /api/auth/me` | authenticated |
  | `/actuator/health` | public |

- The gateway removes any client-supplied `X-User-Id` / `X-User-Roles` headers and sets them
  from the validated token. Downstream services trust these headers. This trust boundary is
  valid only because the services are not exposed outside the Compose network; the README
  states this.

## 5. Service APIs

All paths are as seen through the gateway. Errors use RFC 9457 problem details.

### auth-service

| Method | Path | Body | Result |
|---|---|---|---|
| POST | `/api/auth/register` | `username`, `password` (min 8), `email` | 201 user; 409 if username taken |
| POST | `/api/auth/login` | `username`, `password` | 200 `accessToken`, `tokenType`, `expiresIn`; 401 on bad credentials |
| GET | `/api/auth/me` | — | 200 current user |
| GET | `/.well-known/jwks.json` | — | JWK set (not routed through the gateway) |

Table `users`: `id`, `username` (unique), `email`, `password_hash`, `role` (`USER`/`ADMIN`),
`created_at`.

### product-service

| Method | Path | Result |
|---|---|---|
| GET | `/api/products?page&size` | page of products |
| GET | `/api/products/{id}` | 200 or 404 |
| POST | `/api/products` | 201; 409 if SKU exists |
| PUT | `/api/products/{id}` | 200 or 404 |
| DELETE | `/api/products/{id}` | 204 or 404 |

Table `products`: `id`, `sku` (unique), `name`, `description`, `price` (numeric 12,2, > 0),
`created_at`, `updated_at`.

### inventory-service

| Method | Path | Body | Result |
|---|---|---|---|
| GET | `/api/inventory/{productId}` | — | 200 `productId`, `available`; 404 if unknown |
| PUT | `/api/inventory/{productId}` | `quantity` (>= 0) | 200, creates or replaces the stock level |
| POST | `/api/inventory/reservations` | `orderRef`, `items[{productId, quantity}]` | 201; 409 if any item is short (nothing reserved) |
| DELETE | `/api/inventory/reservations/{orderRef}` | — | 204, returns reserved quantities to stock; no-op if none |

Tables: `inventory_items` (`product_id` PK, `available`), `reservations` (`id`, `order_ref`,
`product_id`, `quantity`, unique on `order_ref` + `product_id`).

Reservation rules:
- Each item is decremented with a conditional update
  (`available = available - q WHERE available >= q`), so concurrent orders cannot oversell.
- The whole request runs in one transaction; one short item rolls back all of it.
- Reserving an `orderRef` that already has reservations returns success without changes.

### order-service

| Method | Path | Body | Result |
|---|---|---|---|
| POST | `/api/orders` | `items[{productId, quantity}]` (1+ items, quantity >= 1) | 201 order; 422 unknown product; 409 insufficient stock; 503 dependency down |
| GET | `/api/orders` | — | the caller's orders |
| GET | `/api/orders/{id}` | — | 200; 404 if missing or owned by someone else |
| POST | `/api/orders/{id}/cancel` | — | 200; 409 if already cancelled |

Tables: `orders` (`id`, `order_ref` UUID unique, `user_id`, `status`
`CONFIRMED`/`CANCELLED`, `total`, `created_at`), `order_items` (`id`, `order_id`,
`product_id`, `product_name`, `unit_price`, `quantity`).

### notification-service

| Method | Path | Result |
|---|---|---|
| GET | `/api/notifications` | the caller's notifications, newest first |

Table `notifications`: `id`, `event_id` (unique), `order_id`, `user_id`, `type`, `message`,
`created_at`.

## 6. Order flow

### Place order

1. `order-service` generates an `orderRef` and loads each product from `product-service`.
   A 404 becomes 422.
2. It calls `POST /api/inventory/reservations`. A 409 is passed on as 409.
3. It saves the order as `CONFIRMED` with name and price snapshots and the computed total.
   If the save fails, it calls `DELETE /api/inventory/reservations/{orderRef}` and rethrows.
4. After the transaction commits, it publishes `OrderPlaced` to the `order-events` topic,
   keyed by order id.

### Cancel order

1. Verify ownership and status, release the reservation, set status `CANCELLED`.
2. After commit, publish `OrderCancelled`.

### Event contract

Topic `order-events`, JSON, no type headers:

```json
{
  "eventId": "uuid",
  "type": "ORDER_PLACED | ORDER_CANCELLED",
  "orderId": 1,
  "userId": "42",
  "total": 59.98,
  "occurredAt": "2026-10-07T10:15:30Z"
}
```

The record is defined separately in `order-service` and `notification-service`; there is no
shared library. `notification-service` deserializes to its own record, ignores unknown
fields, and skips events whose `eventId` it has already stored.

Known limitation: publishing after commit can lose an event if the service crashes between
commit and send. A transactional outbox is the fix and is out of scope.

## 7. Resilience and error handling

- `order-service` calls its dependencies with a load-balanced `RestClient`, connect and read
  timeouts, and a Resilience4j circuit breaker per dependency. The time limiter is disabled;
  the HTTP timeouts bound the call.
- 4xx responses are mapped to domain errors and are excluded from circuit-breaker failure
  counts. Connection errors, timeouts, 5xx and an open circuit become 503.
- The gateway wraps each route in a circuit breaker with a `forward:/fallback` handler that
  returns a 503 problem detail.
- Every service has one `@RestControllerAdvice` mapping domain exceptions to problem
  details; bean-validation failures return 400 with field errors.
- The Kafka consumer uses the default error handler with bounded retries; a record that
  still fails is logged and skipped.

## 8. Observability

- All services expose `health`, `info`, `metrics` and `prometheus` actuator endpoints.
- Traces are sampled at 100% and sent to Zipkin; trace ids appear in log lines.
- Prometheus scrapes every service using a static config file in `infra/prometheus/`.

## 9. Docker

- One root `Dockerfile`: a build stage compiles all modules once with the Maven wrapper; the
  runtime stage copies one module's jar, selected by a `MODULE` build argument.
- `docker-compose.yml` defines Postgres (init script creates one database per service),
  Kafka, Zipkin, Prometheus and the eight modules.
- Start order is enforced with health checks: Postgres and Kafka, then `discovery-server`
  and `config-server`, then the business services, then `api-gateway`.

## 10. Testing

Tests run with `./mvnw verify` and need no Docker.

| Module | Tests |
|---|---|
| `discovery-server`, `config-server` | Context loads; config server returns a known property for a service |
| `auth-service` | Register, duplicate username, login success and failure, issued token verifies against the JWKS |
| `product-service` | CRUD through MockMvc on H2, validation errors, duplicate SKU, 404 |
| `inventory-service` | Reserve success, all-or-nothing on shortage, idempotent reserve, release restores stock |
| `order-service` | Place order happy path, unknown product, insufficient stock, compensation on save failure, cancel, ownership check; HTTP clients and Kafka template mocked |
| `notification-service` | Event stored, duplicate event ignored, listing filtered by user |
| `api-gateway` | Access rules per path and role; client-supplied identity headers are replaced |

Test configuration disables the config client, Eureka client and Kafka listeners and uses
H2 in PostgreSQL mode with the same Flyway migrations.

End-to-end coverage is `scripts/smoke-test.sh` (curl and jq) against the Compose stack.

## 11. Repository layout

```
pom.xml
mvnw, mvnw.cmd, .mvn/
Dockerfile, .dockerignore, docker-compose.yml
README.md
infra/postgres/init.sql
infra/prometheus/prometheus.yml
scripts/smoke-test.sh
discovery-server/  config-server/  api-gateway/  auth-service/
product-service/   inventory-service/  order-service/  notification-service/
docs/superpowers/specs/
```
