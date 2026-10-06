# Shop Microservices Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an eight-module Spring Boot microservices reference system (discovery, config, gateway, auth, product, inventory, order, notification) that runs with Docker Compose.

**Architecture:** A Maven multi-module repo. Business services own a PostgreSQL database each, register in Eureka and load settings from a config server. The gateway validates JWTs issued by `auth-service` and forwards identity headers. `order-service` orchestrates orders over REST with circuit breakers and publishes Kafka events that `notification-service` consumes.

**Tech Stack:** Java 27, Maven 3.10.0 (wrapper 3.3.4), Spring Boot 4.1.1, Spring Cloud 2025.1.3, PostgreSQL 18.6, Kafka 4.3.1, Zipkin 3.6.1, Prometheus 3.15.0, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-10-07-shop-microservices-design.md` — read it first; this plan does not repeat its API tables, table schemas or access rules.

## Global Constraints

- Maven group `com.vanmanh49`; base package per module `com.vanmanh49.<name>` with names `discovery`, `config`, `gateway`, `auth`, `product`, `inventory`, `order`, `notification`.
- Versions exactly as in the Tech Stack line. No milestone, RC or snapshot versions. No version overrides for libraries managed by the Spring Boot or Spring Cloud BOMs.
- Java 27 (`<java.version>27</java.version>`). If a tool or library fails on Java 27, stop and report it; do not downgrade.
- No Lombok. Records for DTOs and events, hand-written JPA entities.
- Errors are RFC 9457 problem details (`ProblemDetail`), produced by one `@RestControllerAdvice` per service.
- Identity headers are `X-User-Id` (JWT `sub`) and `X-User-Roles` (comma-separated, e.g. `USER` or `ADMIN`).
- `./mvnw verify` must pass with no Docker running. Tests use H2 (`jdbc:h2:mem:test;MODE=PostgreSQL`) with the real Flyway migrations.
- Spring Boot 4 renamed starters, test-annotation packages and some properties. Use the coordinates in Task 1. When an import or property name does not resolve, look it up (Context7 docs, or the `spring-configuration-metadata.json` inside the dependency jar); do not guess.
- Git commit messages contain no AI attribution: no `Co-Authored-By` trailer, no mention of Claude, Anthropic or AI.
- Environment variables and their defaults:

  | Variable | Default |
  |---|---|
  | `CONFIG_SERVER_URL` | `http://localhost:8888` |
  | `EUREKA_URL` | `http://localhost:8761/eureka` |
  | `DB_HOST` / `DB_USER` / `DB_PASSWORD` | `localhost` / `shop` / `shop` |
  | `KAFKA_BOOTSTRAP` | `localhost:29092` |
  | `ZIPKIN_URL` | `http://localhost:9411/api/v2/spans` |
  | `AUTH_JWKS_URI` | `http://localhost:8081/.well-known/jwks.json` |
  | `ADMIN_USERNAME` / `ADMIN_PASSWORD` | `admin` / `admin12345` |

- Databases: `authdb`, `productdb`, `inventorydb`, `orderdb`, `notificationdb`.

## Review Focus

1. Two orders race for the last unit of stock: exactly one succeeds, the other gets 409, stock never goes negative. → Task 5.
2. An order lists the same `productId` twice: quantities are merged into one line instead of failing on the reservation unique constraint. → Task 6.
3. A dependency is down while placing an order: the caller gets 503 and no reservation is left behind. → Task 6.
4. An expired, tampered or malformed bearer token reaches the gateway: 401, never 500. → Task 8.
5. A malformed (non-JSON) Kafka record arrives: it is skipped and later valid records are still processed. → Task 7.

---

### Task 1: Build skeleton

**Files:**
- Create: `pom.xml`, `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `.gitignore`
- Create: `<module>/pom.xml` and `<module>/src/main/java/com/vanmanh49/<name>/<Name>Application.java` for all eight modules
- Create: `<module>/src/main/resources/application.yml` for all eight modules

**Interfaces:**
- Produces: a buildable reactor; main classes `DiscoveryServerApplication`, `ConfigServerApplication`, `ApiGatewayApplication`, `AuthServiceApplication`, `ProductServiceApplication`, `InventoryServiceApplication`, `OrderServiceApplication`, `NotificationServiceApplication`.

- [ ] **Step 1: Check the JDK.** Run `/usr/libexec/java_home -v 27`. If it fails, stop and ask the user to approve installing JDK 27 (`brew install --cask temurin@27`); do not continue on JDK 25.
- [ ] **Step 2: Add the Maven wrapper.** Download `https://start.spring.io/starter.zip?type=maven-project&bootVersion=4.1.1&javaVersion=27` into a scratch directory, copy `mvnw`, `mvnw.cmd`, `.mvn/` and `.gitignore`, and set `distributionUrl` to `https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip`.
- [ ] **Step 3: Write the parent `pom.xml`.** Parent `org.springframework.boot:spring-boot-starter-parent:4.1.1`; `com.vanmanh49:shop-microservices:0.1.0-SNAPSHOT`, packaging `pom`; the eight modules; properties `java.version=27`, `spring-cloud.version=2025.1.3`; import `org.springframework.cloud:spring-cloud-dependencies`.
- [ ] **Step 4: Write the module POMs** with `spring-boot-maven-plugin` and these dependencies (`boot:` = `org.springframework.boot:spring-boot-starter-`, `cloud:` = `org.springframework.cloud:`):

  | Module | Dependencies |
  |---|---|
  | `discovery-server` | `cloud:spring-cloud-starter-netflix-eureka-server`, `boot:actuator`; test `boot:test` |
  | `config-server` | `cloud:spring-cloud-config-server`, `boot:actuator`; test `boot:test` |
  | all six below | `cloud:spring-cloud-starter-config`, `cloud:spring-cloud-starter-netflix-eureka-client`, `boot:actuator`, `boot:zipkin`, `io.micrometer:micrometer-registry-prometheus` (runtime) |
  | `api-gateway` | `cloud:spring-cloud-starter-gateway-server-webmvc`, `boot:security-oauth2-resource-server`, `cloud:spring-cloud-starter-circuitbreaker-resilience4j`; test `boot:webmvc-test`, `boot:security-oauth2-resource-server-test` |
  | the five JPA services | `boot:webmvc`, `boot:validation`, `boot:data-jpa`, `boot:flyway`, `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql` (runtime); test `boot:webmvc-test`, `boot:data-jpa-test`, `boot:flyway-test`, `com.h2database:h2` |
  | `auth-service` adds | `org.springframework.security:spring-security-crypto`, `org.springframework.security:spring-security-oauth2-jose` |
  | `order-service` adds | `boot:restclient`, `boot:kafka`, `cloud:spring-cloud-starter-circuitbreaker-resilience4j`; test `boot:restclient-test`, `boot:kafka-test` |
  | `notification-service` adds | `boot:kafka`; test `boot:kafka-test` |

- [ ] **Step 5: Write each main class and `application.yml`.** `discovery-server`: `@EnableEurekaServer`, port 8761, `eureka.client.register-with-eureka=false`, `fetch-registry=false`. `config-server`: `@EnableConfigServer`, port 8888, profile `native`, `spring.cloud.config.server.native.search-locations=classpath:/config/`. The six clients: only `spring.application.name=<module>` and `spring.config.import=configserver:${CONFIG_SERVER_URL:http://localhost:8888}`.
- [ ] **Step 6: Verify.** Run `JAVA_HOME=$(/usr/libexec/java_home -v 27) ./mvnw -q -DskipTests package`. Expected: `BUILD SUCCESS` and a jar in each `target/`. Use this `JAVA_HOME` prefix for every `./mvnw` command in later tasks.
- [ ] **Step 7: Commit.** `git add -A && git commit -m "Add Maven multi-module skeleton"`

### Task 2: Discovery server and config server

**Files:**
- Create: `config-server/src/main/resources/config/application.yml`
- Create: `config-server/src/main/resources/config/{api-gateway,auth-service,product-service,inventory-service,order-service,notification-service}.yml`
- Test: `discovery-server/src/test/java/com/vanmanh49/discovery/DiscoveryServerApplicationTests.java`
- Test: `config-server/src/test/java/com/vanmanh49/config/ConfigServerApplicationTests.java`

**Interfaces:**
- Produces: property names later tasks read — `shop.security.jwt.ttl` (auth, `PT1H`), `shop.security.admin.username` / `.password` (auth), `shop.clients.product.base-url=http://product-service`, `shop.clients.inventory.base-url=http://inventory-service` (order), `shop.kafka.order-events-topic=order-events` (order, notification).

- [ ] **Step 1: Write the failing tests.**
  - `DiscoveryServerApplicationTests.contextLoads()` with `@SpringBootTest`.
  - `ConfigServerApplicationTests.servesProductServicePort()`: `@SpringBootTest(webEnvironment = RANDOM_PORT)`; GET `/product-service/default`; assert status 200 and the body contains `"server.port":8082`.
  - `ConfigServerApplicationTests.servesSharedEurekaSetting()`: same endpoint; body contains `eureka.client.service-url.defaultZone`.
- [ ] **Step 2: Run** `./mvnw -q -pl discovery-server,config-server test`. Expected: the two config tests FAIL (404 / missing property).
- [ ] **Step 3: Write `config/application.yml`** (shared): Eureka `defaultZone=${EUREKA_URL:...}`, `eureka.instance.prefer-ip-address=true`; actuator exposure `health,info,metrics,prometheus`; health probes enabled; tracing sampling probability `1.0`; Zipkin endpoint `${ZIPKIN_URL:...}`; MVC problem details enabled; `spring.jpa.hibernate.ddl-auto=validate`; `spring.jpa.open-in-view=false`.
- [ ] **Step 4: Write the per-service files.** Ports per the spec's module table. JPA services: datasource `jdbc:postgresql://${DB_HOST:localhost}:5432/<db>` with `DB_USER`/`DB_PASSWORD`. `auth-service`: `shop.security.*`. `order-service` and `notification-service`: `spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP:...}`, topic property; order uses the JSON serializer with type headers off; notification uses `ErrorHandlingDeserializer` delegating to the JSON deserializer with default type `com.vanmanh49.notification.event.OrderEvent`, group id `notification-service`, `auto-offset-reset=earliest`. `order-service`: HTTP client connect timeout 2s, read timeout 3s; `spring.cloud.circuitbreaker.resilience4j.disable-time-limiter=true`; Resilience4j default config ignoring `HttpClientErrorException`; client base URLs. `api-gateway`: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${AUTH_JWKS_URI:...}`.
- [ ] **Step 5: Run** the Step 2 command. Expected: PASS.
- [ ] **Step 6: Commit.** `git add -A && git commit -m "Add discovery server and config server with service configuration"`

### Task 3: auth-service

**Files:**
- Create under `auth-service/src/main/java/com/vanmanh49/auth/`: `user/User.java`, `user/Role.java`, `user/UserRepository.java`, `token/JwtKeys.java`, `token/TokenService.java`, `web/AuthController.java`, `web/JwksController.java`, `web/dto/{RegisterRequest,LoginRequest,UserResponse,TokenResponse}.java`, `web/ApiExceptionHandler.java`, `AuthService.java`, `AdminSeeder.java`, `SecurityProperties.java`
- Create: `auth-service/src/main/resources/db/migration/V1__create_users.sql`
- Create: `auth-service/src/test/resources/application.yml`
- Test: `auth-service/src/test/java/com/vanmanh49/auth/AuthFlowTests.java`

**Interfaces:**
- Produces: JWT signed RS256 with claims `sub` (user id as string), `username`, `roles` (JSON array of role names), `exp` = issued + `shop.security.jwt.ttl`; JWKS at `GET /.well-known/jwks.json`.
- `TokenService.issue(User user): TokenResponse`; `TokenResponse(String accessToken, String tokenType, long expiresIn)` with `tokenType` `"Bearer"`.
- `AuthService.register(RegisterRequest): UserResponse` throws `UsernameTakenException` (409); `AuthService.login(LoginRequest): TokenResponse` throws `BadCredentialsException` (401).
- `GET /api/auth/me` reads the `X-User-Id` header; missing header → 401.

- [ ] **Step 1: Write the test config.** `src/test/resources/application.yml`: `spring.cloud.config.enabled=false`, `spring.config.import` empty, `eureka.client.enabled=false`, H2 URL from Global Constraints, `shop.security.jwt.ttl=PT1H`, admin `admin`/`admin12345`, tracing disabled. Reuse this file's common part in Tasks 4–7.
- [ ] **Step 2: Write failing tests** in `AuthFlowTests` (`@SpringBootTest` + `@AutoConfigureMockMvc`):
  - `registerReturns201AndHidesPassword`: POST register `{"username":"alice","password":"password1","email":"a@example.com"}` → 201, `$.username == "alice"`, `$.role == "USER"`, no `password` or `passwordHash` field.
  - `registerDuplicateUsernameReturns409`.
  - `registerShortPasswordReturns400`: password `"short"` → 400 with a `password` field error.
  - `loginReturnsTokenThatVerifiesAgainstJwks`: fetch `/.well-known/jwks.json`, build a `NimbusJwtDecoder` from the returned key, decode `accessToken`; assert `sub` equals the registered user id, `roles == ["USER"]`, `expiresIn == 3600`.
  - `loginWrongPasswordReturns401` and `loginUnknownUserReturns401` (same problem-detail body for both).
  - `adminIsSeeded`: login as `admin`/`admin12345` → token `roles == ["ADMIN"]`.
  - `meReturnsCurrentUser`: GET `/api/auth/me` with `X-User-Id` → 200; without it → 401.
- [ ] **Step 3: Run** `./mvnw -q -pl auth-service test`. Expected: FAIL (no controllers).
- [ ] **Step 4: Implement.** Migration per the spec's `users` table (`bigint generated by default as identity` ids — use this id style in every service). `JwtKeys` generates a 2048-bit RSA pair once at startup with a random `kid`. `TokenService` uses `NimbusJwtEncoder`. Passwords use `BCryptPasswordEncoder`. `AdminSeeder` is an `ApplicationRunner` that creates the admin only if the username does not exist.
- [ ] **Step 5: Run** the Step 3 command. Expected: PASS.
- [ ] **Step 6: Commit.** `git add -A && git commit -m "Add auth-service with registration, login and JWKS"`

### Task 4: product-service

**Files:**
- Create under `product-service/src/main/java/com/vanmanh49/product/`: `Product.java`, `ProductRepository.java`, `ProductService.java`, `web/ProductController.java`, `web/dto/{ProductRequest,ProductResponse}.java`, `web/ApiExceptionHandler.java`
- Create: `product-service/src/main/resources/db/migration/V1__create_products.sql`, `product-service/src/test/resources/application.yml`
- Test: `product-service/src/test/java/com/vanmanh49/product/ProductApiTests.java`

**Interfaces:**
- Produces: `ProductResponse(Long id, String sku, String name, String description, BigDecimal price)` — the JSON shape `order-service` reads from `GET /api/products/{id}`.
- `ProductRequest(String sku, String name, String description, BigDecimal price)`: `sku` and `name` not blank, `price` > 0 with at most 2 decimals.

- [ ] **Step 1: Write failing tests** in `ProductApiTests` (`@SpringBootTest` + MockMvc, `@Transactional`):
  - `createReturns201WithLocation`: POST `{"sku":"SKU-1","name":"Keyboard","description":"Mechanical","price":49.99}` → 201, `Location` ends with `/api/products/{id}`, `$.price == 49.99`.
  - `createDuplicateSkuReturns409`.
  - `createInvalidReturns400`: blank name and price `0` → 400 with field errors for `name` and `price`.
  - `getMissingReturns404`, `updateMissingReturns404`, `deleteMissingReturns404`.
  - `updateChangesFields` → 200; `deleteReturns204` then GET → 404.
  - `listIsPagedAndPageSizeIsCapped`: create 3; GET `?page=0&size=2` → `$.content.length() == 2`, `$.page.totalElements == 3`; GET `?size=100000` → reported page size is 100.
- [ ] **Step 2: Run** `./mvnw -q -pl product-service test`. Expected: FAIL.
- [ ] **Step 3: Implement.** Migration per the spec's `products` table. List endpoint returns Spring Data's stable page JSON (`PagedModel`), sorted by id, max page size 100.
- [ ] **Step 4: Run** the Step 2 command. Expected: PASS.
- [ ] **Step 5: Commit.** `git add -A && git commit -m "Add product-service catalog API"`

### Task 5: inventory-service

**Files:**
- Create under `inventory-service/src/main/java/com/vanmanh49/inventory/`: `InventoryItem.java`, `Reservation.java`, `InventoryItemRepository.java`, `ReservationRepository.java`, `InventoryService.java`, `web/InventoryController.java`, `web/dto/{StockRequest,StockResponse,ReservationRequest,ReservationItem}.java`, `web/ApiExceptionHandler.java`
- Create: `inventory-service/src/main/resources/db/migration/V1__create_inventory.sql`, `inventory-service/src/test/resources/application.yml`
- Test: `inventory-service/src/test/java/com/vanmanh49/inventory/InventoryApiTests.java`, `.../ReservationConcurrencyTests.java`

**Interfaces:**
- Produces (consumed by `order-service`): `POST /api/inventory/reservations` with `ReservationRequest(String orderRef, List<ReservationItem> items)`, `ReservationItem(Long productId, int quantity)` → 201 or 409; `DELETE /api/inventory/reservations/{orderRef}` → 204.
- `InventoryItemRepository.decrement(Long productId, int quantity): int` — `@Modifying` update `available = available - :q WHERE productId = :id AND available >= :q`; returns rows changed.
- `InventoryService.reserve(ReservationRequest)` throws `InsufficientStockException(productId)` (409); `InventoryService.release(String orderRef)`.

- [ ] **Step 1: Write failing tests.**
  - `InventoryApiTests.putCreatesThenReplacesStock`: PUT `/api/inventory/1` `{"quantity":5}` → `$.available == 5`; PUT `{"quantity":2}` → 2. `putNegativeReturns400`. `getUnknownReturns404`.
  - `reserveDecrementsStock`: stock 5; reserve `orderRef "o-1"` quantity 3 → 201; GET → `available == 2`.
  - `reserveIsAllOrNothing`: product 1 stock 5, product 2 stock 1; reserve `{1:2, 2:3}` → 409; both stock levels unchanged; no reservation rows.
  - `reserveUnknownProductReturns409`.
  - `reserveSameOrderRefTwiceIsIdempotent`: second POST → 201, stock decremented once.
  - `releaseRestoresStock` → 204 and stock back to 5; `releaseUnknownOrderRefReturns204`.
  - `ReservationConcurrencyTests.onlyOneOfTwoConcurrentReservationsWins` (not `@Transactional`): stock 1; two threads behind a `CountDownLatch` each reserve quantity 1 with different `orderRef`s; assert exactly one succeeds, one throws `InsufficientStockException`, final `available == 0`.
- [ ] **Step 2: Run** `./mvnw -q -pl inventory-service test`. Expected: FAIL.
- [ ] **Step 3: Implement.** Migration per the spec's tables. `reserve` and `release` are `@Transactional`; `reserve` checks for existing reservations of the `orderRef` first, then calls `decrement` per item and throws when it returns 0.
- [ ] **Step 4: Run** the Step 2 command. Expected: PASS.
- [ ] **Step 5: Commit.** `git add -A && git commit -m "Add inventory-service with atomic reservations"`

### Task 6: order-service

**Files:**
- Create under `order-service/src/main/java/com/vanmanh49/order/`: `Order.java`, `OrderItem.java`, `OrderStatus.java`, `OrderRepository.java`, `OrderService.java`, `OrderStore.java`, `client/ProductClient.java`, `client/InventoryClient.java`, `client/ClientConfig.java`, `client/dto/{ProductDto,ReservationRequest,ReservationItem}.java`, `event/OrderEvent.java`, `event/OrderEventPublisher.java`, `web/OrderController.java`, `web/dto/{PlaceOrderRequest,OrderLine,OrderResponse}.java`, `web/ApiExceptionHandler.java`
- Create: `order-service/src/main/resources/db/migration/V1__create_orders.sql`, `order-service/src/test/resources/application.yml`
- Test: `order-service/src/test/java/com/vanmanh49/order/OrderApiTests.java`, `.../client/ClientErrorMappingTests.java`

**Interfaces:**
- Consumes: product and inventory HTTP contracts from Tasks 4 and 5.
- `ProductClient.get(Long id): ProductDto` throws `ProductNotFoundException` (→ 422) or `DependencyUnavailableException` (→ 503).
- `InventoryClient.reserve(String orderRef, List<ReservationItem> items)` throws `InsufficientStockException` (→ 409) or `DependencyUnavailableException`; `InventoryClient.release(String orderRef)`.
- `OrderStore.saveConfirmed(...)` / `markCancelled(...)` — the `@Transactional` persistence step, a separate bean so `OrderService` can compensate when it fails.
- Produces: `OrderEvent(UUID eventId, String type, Long orderId, String userId, BigDecimal total, Instant occurredAt)` with `type` `ORDER_PLACED` or `ORDER_CANCELLED`, sent to `${shop.kafka.order-events-topic}` keyed by the order id as a string.
- `OrderEventPublisher.publish(OrderEvent)` — called from a `@TransactionalEventListener(phase = AFTER_COMMIT)`.

- [ ] **Step 1: Write failing tests.** `OrderApiTests`: `@SpringBootTest` + MockMvc with `@MockitoBean ProductClient`, `InventoryClient`, `OrderEventPublisher`; test config also sets `spring.kafka.admin.auto-create=false`. All requests send `X-User-Id: 42`.
  - `placeOrderReturns201AndPublishesEvent`: product 1 `Keyboard` at `49.99`, quantity 2 → 201, `$.status == "CONFIRMED"`, `$.total == 99.98`, `$.items[0].productName == "Keyboard"`; `reserve` called once; publisher receives `type == "ORDER_PLACED"` and `userId == "42"`.
  - `duplicateProductLinesAreMerged`: items `[{1,1},{1,2}]` → `reserve` called with one item of quantity 3; response has one line.
  - `unknownProductReturns422AndReservesNothing`.
  - `insufficientStockReturns409AndSavesNothing`.
  - `dependencyDownReturns503AndLeavesNoReservation`: `productClient.get` throws `DependencyUnavailableException` → 503, `reserve` never called; second case: `reserve` throws it → 503, no order row.
  - `saveFailureReleasesReservation`: `@MockitoSpyBean OrderStore` throwing on `saveConfirmed` → `inventoryClient.release(orderRef)` called with the same `orderRef` passed to `reserve`; no event published.
  - `emptyItemsReturns400`, `zeroQuantityReturns400`, `missingUserHeaderReturns401`.
  - `listReturnsOnlyCallersOrders` and `getOtherUsersOrderReturns404` (order placed as 42, read as 43).
  - `cancelReleasesStockAndPublishesEvent` → 200, `CANCELLED`, `release` called, event `ORDER_CANCELLED`; `cancelTwiceReturns409`.
  - `ClientErrorMappingTests` (`@RestClientTest` or `MockRestServiceServer`): product 404 → `ProductNotFoundException`; inventory 409 → `InsufficientStockException`; 500 and connection failure → `DependencyUnavailableException`.
- [ ] **Step 2: Run** `./mvnw -q -pl order-service test`. Expected: FAIL.
- [ ] **Step 3: Implement.** Migration per the spec's tables. `ClientConfig` declares a `@LoadBalanced RestClient.Builder` and builds one `RestClient` per base URL. Each client call runs inside `CircuitBreakerFactory.create("product" | "inventory").run(call, fallback)`; the fallback maps `HttpClientErrorException` 404/409 to the domain exception and everything else to `DependencyUnavailableException`. `OrderService.place` follows the spec's "Place order" steps, merging duplicate product ids first.
- [ ] **Step 4: Run** the Step 2 command. Expected: PASS.
- [ ] **Step 5: Commit.** `git add -A && git commit -m "Add order-service with order saga and events"`

### Task 7: notification-service

**Files:**
- Create under `notification-service/src/main/java/com/vanmanh49/notification/`: `Notification.java`, `NotificationRepository.java`, `NotificationService.java`, `event/OrderEvent.java`, `event/OrderEventListener.java`, `web/NotificationController.java`, `web/dto/NotificationResponse.java`, `web/ApiExceptionHandler.java`
- Create: `notification-service/src/main/resources/db/migration/V1__create_notifications.sql`, `notification-service/src/test/resources/application.yml`
- Test: `notification-service/src/test/java/com/vanmanh49/notification/NotificationTests.java`, `.../event/OrderEventDeserializationTests.java`

**Interfaces:**
- Consumes: the `OrderEvent` JSON from Task 6 (own copy of the record, unknown fields ignored).
- `NotificationService.handle(OrderEvent event)` — stores one notification; a repeated `eventId` is a no-op.
- `OrderEventListener.onOrderEvent(OrderEvent event)` — `@KafkaListener(topics = "${shop.kafka.order-events-topic}")` delegating to `handle`.

- [ ] **Step 1: Write failing tests.** Test config sets `spring.kafka.listener.auto-startup=false`.
  - `NotificationTests.orderPlacedIsStored`: `handle` an `ORDER_PLACED` event for user `42`, order 7, total `99.98` → one row whose message contains `7` and `99.98`.
  - `duplicateEventIsIgnored`: same event twice → one row, no exception.
  - `listReturnsOnlyCallersNotificationsNewestFirst`: events for users 42 and 43 → GET `/api/notifications` with `X-User-Id: 42` returns only user 42's, newest first; without the header → 401.
  - `OrderEventDeserializationTests.malformedRecordIsSkippedAndNextIsProcessed`: build the consumer's value deserializer with the same properties as `notification-service.yml`; deserializing `not json` yields a deserialization failure handled by `ErrorHandlingDeserializer` (null value plus error header, no exception thrown); deserializing a valid payload with an extra unknown field yields an `OrderEvent`.
- [ ] **Step 2: Run** `./mvnw -q -pl notification-service test`. Expected: FAIL.
- [ ] **Step 3: Implement.** Migration per the spec's table. Idempotency: check `existsByEventId` and rely on the unique constraint as the backstop. A `DefaultErrorHandler` bean with a `FixedBackOff(1000, 2)` logs and skips records that still fail.
- [ ] **Step 4: Run** the Step 2 command. Expected: PASS.
- [ ] **Step 5: Commit.** `git add -A && git commit -m "Add notification-service consuming order events"`

### Task 8: api-gateway

**Files:**
- Create under `api-gateway/src/main/java/com/vanmanh49/gateway/`: `SecurityConfig.java`, `RouteConfig.java`, `IdentityHeaders.java`, `FallbackController.java`
- Create: `api-gateway/src/test/resources/application.yml`
- Test: `api-gateway/src/test/java/com/vanmanh49/gateway/AccessRulesTests.java`, `.../IdentityHeadersTests.java`

**Interfaces:**
- Consumes: JWT claims from Task 3 (`sub`, `roles`); service ids `auth-service`, `product-service`, `inventory-service`, `order-service`, `notification-service`.
- `IdentityHeaders.apply(ServerRequest request): ServerRequest` — removes inbound `X-User-Id` / `X-User-Roles`, then sets them from the authenticated `Jwt` when present.
- Routes: `/api/auth/**` → `auth-service`, `/api/products/**` → `product-service`, `/api/inventory/**` → `inventory-service`, `/api/orders/**` → `order-service`, `/api/notifications/**` → `notification-service`; each load-balanced and wrapped in a circuit breaker with fallback `forward:/fallback`.
- `GET|POST|PUT|DELETE /fallback` → 503 problem detail with title `Service unavailable`.

- [ ] **Step 1: Write failing tests.** `AccessRulesTests`: `@SpringBootTest` + MockMvc, `@MockitoBean JwtDecoder`, routes pointed at an unresolvable service so an allowed request ends in the 503 fallback (allowed = not 401/403). Use `jwt()` request post-processors with a `roles` claim.
  - Public: POST `/api/auth/login`, POST `/api/auth/register`, GET `/api/products`, GET `/actuator/health` → not 401/403 without a token.
  - `GET /api/orders` without a token → 401; with a `USER` token → not 401/403.
  - `POST /api/products` with `USER` → 403; with `ADMIN` → not 403. Same for `PUT /api/inventory/1`.
  - `GET /api/inventory/1` with `USER` → not 403.
  - `POST /api/inventory/reservations` and `DELETE /api/inventory/reservations/x` with `ADMIN` → 403.
  - `malformedOrExpiredTokenReturns401`: decoder throws `BadJwtException` for the header `Bearer garbage` → 401, not 500.
  - `fallbackReturns503ProblemDetail`.
  - `IdentityHeadersTests.replacesClientSuppliedHeaders`: request with `X-User-Id: 999`, authenticated JWT `sub=42`, `roles=["USER"]` → result has `X-User-Id: 42`, `X-User-Roles: USER`. `stripsHeadersWhenAnonymous`: no authentication → both headers absent.
- [ ] **Step 2: Run** `./mvnw -q -pl api-gateway test`. Expected: FAIL.
- [ ] **Step 3: Implement.** `SecurityConfig`: stateless, CSRF off, the spec's access-rule table in order (reservation denial before the other inventory rules), a `JwtAuthenticationConverter` mapping the `roles` claim to `ROLE_` authorities. `RouteConfig`: `RouterFunction<ServerResponse>` beans built with the gateway WebMVC Java DSL (`GatewayRouterFunctions.route`, `HandlerFunctions.http`, the load-balancer and circuit-breaker filter functions, `.before(IdentityHeaders::apply)`); confirm the DSL package names against the 2025.1.3 jar.
- [ ] **Step 4: Run** the Step 2 command. Expected: PASS.
- [ ] **Step 5: Run the whole build.** `./mvnw verify`. Expected: `BUILD SUCCESS` for all nine reactor entries.
- [ ] **Step 6: Commit.** `git add -A && git commit -m "Add api-gateway with JWT access rules and routing"`

### Task 9: Docker Compose, smoke test and README

**Files:**
- Create: `Dockerfile`, `.dockerignore`, `docker-compose.yml`, `infra/postgres/init.sql`, `infra/prometheus/prometheus.yml`, `scripts/smoke-test.sh`, `README.md`

**Interfaces:**
- Consumes: every module's jar at `<module>/target/<module>-0.1.0-SNAPSHOT.jar`, ports and environment variables from Global Constraints.

- [ ] **Step 1: Write `scripts/smoke-test.sh`** (bash, `set -euo pipefail`, needs `curl` and `jq`, base URL `${BASE_URL:-http://localhost:8080}`). It prints one `PASS`/`FAIL` line per check and exits non-zero on the first failure. Checks, in order:
  1. Register a user with a random suffix → 201.
  2. Login as that user and as `admin`/`admin12345` → tokens.
  3. `POST /api/products` with the user token → 403; with the admin token → 201.
  4. `PUT /api/inventory/{id}` `{"quantity":5}` as admin → `available == 5`.
  5. `GET /api/products/{id}` with no token → 200.
  6. `POST /api/orders` with no token → 401.
  7. Order quantity 2 as the user → 201, `status == "CONFIRMED"`; stock is 3.
  8. Order quantity 99 → 409; stock still 3.
  9. `GET /api/notifications` shows an `ORDER_PLACED` entry within 30 seconds (poll).
  10. Cancel the order → `CANCELLED`; stock is 5; an `ORDER_CANCELLED` notification appears.
  11. `POST /api/inventory/reservations` through the gateway → 403.
- [ ] **Step 2: Write the `Dockerfile`.** Stage `build` on `eclipse-temurin:27-jdk`: copy the repo, run `./mvnw -q -B -DskipTests package` with a BuildKit cache mount on `/root/.m2`. Stage `runtime` on `eclipse-temurin:27-jre`: `ARG MODULE`, copy that module's jar to `/app/app.jar`, run as a non-root user, `ENTRYPOINT ["java","-jar","/app/app.jar"]`. Confirm both image tags exist with `docker manifest inspect`; if a `27` tag is missing, stop and report. `.dockerignore`: `**/target`, `.git`, `docs`.
- [ ] **Step 3: Write `docker-compose.yml` and infra files.**
  - `postgres:18.6` with `shop`/`shop`, `infra/postgres/init.sql` creating the five databases, `pg_isready` health check, named volume.
  - `apache/kafka:4.3.1` single-node KRaft with listeners `INTERNAL://kafka:9092` and `EXTERNAL://localhost:29092`, replication factors 1, health check using the broker API versions script.
  - `openzipkin/zipkin:3.6.1` on 9411; `prom/prometheus:v3.15.0` on 9090 scraping `/actuator/prometheus` on all eight modules by service name.
  - Eight app services built from the root `Dockerfile` with `MODULE`; environment sets `CONFIG_SERVER_URL=http://config-server:8888`, `EUREKA_URL=http://discovery-server:8761/eureka`, `DB_HOST=postgres`, `KAFKA_BOOTSTRAP=kafka:9092`, `ZIPKIN_URL=http://zipkin:9411/api/v2/spans`, `AUTH_JWKS_URI=http://auth-service:8081/.well-known/jwks.json`, admin credentials.
  - Each app has a health check on `/actuator/health`; `depends_on` with `condition: service_healthy` in the order from spec section 9. Only 8080, 8761, 9411 and 9090 are published.
- [ ] **Step 4: Start the stack.** Docker Desktop must be running — ask the user to start it if `docker info` fails. Run `docker compose up --build -d`, then `docker compose ps`. Expected: all twelve containers `healthy` (allow several minutes on first build).
- [ ] **Step 5: Run the smoke test.** `./scripts/smoke-test.sh`. Expected: eleven `PASS` lines, exit code 0. On failure, read `docker compose logs <service>`, fix the cause in the owning module with a test that reproduces it, and rerun.
- [ ] **Step 6: Check observability.** `curl -s localhost:9090/api/v1/targets | jq '[.data.activeTargets[].health] | unique'` → `["up"]`; `curl -s 'localhost:9411/api/v2/services'` lists `api-gateway` and `order-service`.
- [ ] **Step 7: Write `README.md`.** Sections: what this is; architecture table and an ASCII request-flow diagram; prerequisites; run with Compose; walkthrough with `curl` examples matching the smoke test; URLs (gateway, Eureka, Zipkin, Prometheus); running tests; where each pattern lives (pattern → module → class); shortcuts and next steps (the spec's out-of-scope list, the header trust boundary, development admin credentials, ephemeral signing key, publish-after-commit gap).
- [ ] **Step 8: Stop the stack and commit.** `docker compose down`, then `git add -A && git commit -m "Add Docker Compose stack, smoke test and README"`
