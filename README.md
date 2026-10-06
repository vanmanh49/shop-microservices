# Shop Microservices

A reference microservices system built with Spring Boot. It is meant for reading and
experimenting: a small shop (products, stock, orders) that shows the common patterns
working together, with every shortcut called out.

| | |
|---|---|
| Language / build | Java 27, Maven 3.10.0 (wrapper) |
| Framework | Spring Boot 4.1.1, Spring Cloud 2025.1.3 |
| Infrastructure | PostgreSQL 18.6, Apache Kafka 4.3.1, Zipkin 3.6.1, Prometheus 3.15.0 |
| Runtime | Docker Compose |

## Architecture

```
                         ┌──────────────────┐      ┌───────────────┐
        client ────────► │   api-gateway    │◄────►│ auth-service  │ (JWKS: public key)
                         │ JWT check, routes│      └───────────────┘
                         └────────┬─────────┘
            ┌──────────────┬──────┴────────┬──────────────────┐
            ▼              ▼               ▼                  ▼
     product-service  inventory-service  order-service   notification-service
            ▲              ▲               │  │                  ▲
            └──── REST ────┴───────────────┘  └──── Kafka ───────┘
                                                 (order-events)

  All services: register in discovery-server (Eureka), load settings from config-server,
  send traces to Zipkin, expose metrics to Prometheus, own a PostgreSQL database.
```

| Module | Port | Role |
|---|---|---|
| `discovery-server` | 8761 | Eureka registry: services find each other by name |
| `config-server` | 8888 | Serves the settings of every service from files in this repo |
| `api-gateway` | 8080 | The only public entry point: routing, token validation, fallback |
| `auth-service` | 8081 | Registration, login, issues signed JWTs |
| `product-service` | 8082 | Product catalog |
| `inventory-service` | 8083 | Stock levels and reservations |
| `order-service` | 8084 | Places and cancels orders, publishes order events |
| `notification-service` | 8085 | Turns order events into notifications |

Only the gateway (8080), the Eureka dashboard (8761), Zipkin (9411) and Prometheus (9090)
are published to your machine. The business services are reachable only from inside the
Compose network.

### What happens when an order is placed

1. The client sends `POST /api/orders` with a bearer token to the gateway.
2. The gateway validates the token and forwards the request with `X-User-Id` and
   `X-User-Roles` headers.
3. `order-service` reads each product's name and price from `product-service`.
4. It asks `inventory-service` to reserve the stock. The reservation is all-or-nothing; if
   anything is short the order is rejected with `409`.
5. It saves the order. If saving fails, it releases the reservation again.
6. After the database commit it publishes an `ORDER_PLACED` event to Kafka.
7. `notification-service` consumes the event and stores a notification for the user.

Cancelling an order releases the stock and publishes `ORDER_CANCELLED`.

## Run it

You need Docker (with Compose). Nothing else: the build runs inside the image.

```bash
docker compose up --build -d     # first build takes several minutes
docker compose ps                # wait until every service is "healthy"
./scripts/smoke-test.sh          # end-to-end check; needs curl and jq
```

The stack runs twelve containers and needs roughly 4 GB of memory.

Right after startup the gateway can answer `503 Service unavailable` for a few seconds:
"healthy" means a service has started, and the gateway still has to learn its address from
Eureka. The smoke test waits for this; by hand, just retry.

| URL | What |
|---|---|
| http://localhost:8080 | API gateway |
| http://localhost:8761 | Eureka dashboard: which services are registered |
| http://localhost:9411 | Zipkin: follow one request across services |
| http://localhost:9090 | Prometheus: metrics of every service |

Stop with `docker compose down`. Add `-v` to also delete the database volume.

## Try it by hand

```bash
BASE=http://localhost:8080

# Register and log in
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123","email":"alice@example.com"}'
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123"}' | jq -r .accessToken)

# Log in as the seeded admin (development credentials, see docker-compose.yml)
ADMIN=$(curl -s -X POST $BASE/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin12345"}' | jq -r .accessToken)

# Admin: create a product and give it stock
curl -s -X POST $BASE/api/products -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' \
  -d '{"sku":"KB-1","name":"Keyboard","description":"Mechanical","price":49.99}'
curl -s -X PUT $BASE/api/inventory/1 -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"quantity":5}'

# Anyone: browse the catalog
curl -s "$BASE/api/products?page=0&size=20"

# User: order, list orders, read notifications, cancel
curl -s -X POST $BASE/api/orders -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"items":[{"productId":1,"quantity":2}]}'
curl -s $BASE/api/orders -H "Authorization: Bearer $TOKEN"
curl -s $BASE/api/notifications -H "Authorization: Bearer $TOKEN"
curl -s -X POST $BASE/api/orders/1/cancel -H "Authorization: Bearer $TOKEN"
```

### API overview

| Method and path | Who | Notes |
|---|---|---|
| `POST /api/auth/register`, `POST /api/auth/login` | anyone | Login returns `accessToken` (valid 1 hour) |
| `GET /api/auth/me` | signed in | |
| `GET /api/products`, `GET /api/products/{id}` | anyone | Paged, at most 100 per page |
| `POST`, `PUT`, `DELETE /api/products...` | admin | |
| `GET /api/inventory/{productId}` | signed in | |
| `PUT /api/inventory/{productId}` | admin | Sets the stock level |
| `POST /api/orders`, `GET /api/orders`, `GET /api/orders/{id}` | signed in | Users see only their own orders |
| `POST /api/orders/{id}/cancel` | signed in | |
| `GET /api/notifications` | signed in | |

Errors are returned as [problem details](https://www.rfc-editor.org/rfc/rfc9457) JSON.

## Tests

```bash
./mvnw verify          # needs JDK 27; no Docker required
```

Each service is tested against an in-memory H2 database with the real Flyway migrations;
calls to other services and to Kafka are replaced by mocks. `scripts/smoke-test.sh` covers
the services working together.

## Where each pattern lives

| Pattern | Where to look |
|---|---|
| Service discovery | `discovery-server`; clients call each other by name (`http://product-service`) |
| Central configuration | `config-server/src/main/resources/configs/`; each service's own `application.yml` only says where the config server is |
| API gateway routing | `api-gateway` → `RouteConfig` |
| Token validation at the edge | `api-gateway` → `SecurityConfig` |
| Passing identity downstream | `api-gateway` → `IdentityHeaders` |
| Issuing JWTs, publishing the public key | `auth-service` → `TokenService`, `JwtKeys`, `JwksController` |
| Database per service, schema migrations | each service's `src/main/resources/db/migration/` |
| Preventing overselling | `inventory-service` → `InventoryItemRepository.decrement`, `InventoryService.reserve` |
| Orchestrated saga with compensation | `order-service` → `OrderService.place` |
| Load-balanced HTTP clients | `order-service` → `ClientConfig`, `ProductClient`, `InventoryClient` |
| Circuit breaker | `order-service` clients; `api-gateway` → `RouteConfig`, `FallbackController` |
| Publishing events after commit | `order-service` → `OrderStore`, `OrderEventRelay`, `OrderEventPublisher` |
| Idempotent event consumer | `notification-service` → `NotificationService.handle` |
| Skipping bad messages | `notification-service` → `OrderEventListener`, and the consumer settings in `configs/notification-service.yml` |
| Consistent error responses | each service's `web/ApiExceptionHandler` |
| Tracing and metrics | shared settings in `configs/application.yml`; `infra/prometheus/prometheus.yml` |

## Shortcuts taken, and what a real system would do

- **Services trust the gateway's headers.** `X-User-Id` and `X-User-Roles` are believed by
  the services without checking a token. That is only safe because nothing but the gateway
  can reach them. In a less controlled network, each service should validate the JWT itself.
- **Development credentials.** The admin account (`admin` / `admin12345`) and the database
  password are set in `docker-compose.yml`. Replace them before exposing the stack.
- **The signing key is regenerated on every start of `auth-service`.** Tokens stop working
  after a restart, and the gateway picks up the new key on the next request. Running more
  than one `auth-service` instance needs a shared key from a secret store.
- **An event can be lost.** `order-service` publishes after the database commit. If it
  stops between the commit and the send, the order exists but no event was sent. A
  transactional outbox closes this gap.
- **A failed compensation is only logged.** If releasing a reservation fails after an order
  could not be saved, the stock stays reserved until someone releases it.
- **Gateway metrics are public.** `/actuator/prometheus` on port 8080 is open so Prometheus
  can scrape it without a token.
- **No shared code between services.** DTOs and the order event are duplicated on purpose,
  so services only depend on each other's JSON.

Not included: Kubernetes manifests, a CI pipeline, Grafana dashboards, a frontend, refresh
tokens, rate limiting.

## Design documents

- Design: `docs/superpowers/specs/2026-10-07-shop-microservices-design.md`
- Implementation plan: `docs/superpowers/plans/2026-10-07-shop-microservices.md`
