# Kubernetes Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the whole shop stack on any Kubernetes cluster with `kubectl apply -k .`.

**Architecture:** Plain manifests in `k8s/`, assembled by a root `kustomization.yaml`. Services keep their Compose names so every existing URL works; Eureka and the config server stay. No Java changes.

**Tech Stack:** Kubernetes manifests (apps/v1, v1), Kustomize as built into `kubectl` v1.36, bash.

**Spec:** `docs/superpowers/specs/2026-10-07-kubernetes-deployment-design.md`

## Global Constraints

- Namespace `shop`, set once in `kustomization.yaml`; manifests carry no `namespace:` field.
- Every Service is `ClusterIP` and is named exactly as its Compose service.
- Every workload has the label `app: <name>`; selectors use only that label.
- Spring images are `shop/<module>:0.1.0`. Infra images are the Compose ones: `postgres:18.6`, `apache/kafka:4.3.1`, `openzipkin/zipkin:3.6.1`, `prom/prometheus:v3.15.0`.
- Ports: discovery-server 8761, config-server 8888, api-gateway 8080, auth 8081, product 8082, inventory 8083, order 8084, notification 8085, postgres 5432, kafka 9092, zipkin 9411, prometheus 9090.
- 1 replica everywhere. No Ingress, HPA, NetworkPolicy or overlays.
- No changes under any `src/` directory, and none to `docker-compose.yml` or `infra/`.
- Commit messages: plain, no AI attribution.

## Review Focus

No cluster is available on the authoring machine, so these are pinned by assertions on the rendered output (`scripts/k8s-check.sh`, Task 1) where that is possible, and by the README otherwise.

1. **Kafka reads `KAFKA_PORT` injected by the `kafka` Service** and fails to start → the Kafka pod has `enableServiceLinks: false` (asserted).
2. **Generated ConfigMaps get a hash suffix**; a volume that names them wrongly mounts nothing → the rendered output contains no reference to the bare names `postgres-init` / `prometheus-config` without a suffix (asserted).
3. **Registry not set** → `ImagePullBackOff` on the 8 Spring pods → README troubleshooting line.
4. **No default storage class** → Postgres PVC stays Pending → README prerequisite and troubleshooting line.
5. **A service starts before config-server/Postgres/Kafka** → it exits and restarts; expected, stated in the README. The startup probe allows 5 minutes so a slow node does not turn this into a liveness kill loop (asserted: `failureThreshold: 30`, `periodSeconds: 10`).

---

### Task 1: Kustomize skeleton, shared config and infrastructure

**Files:**
- Create: `kustomization.yaml`, `scripts/k8s-check.sh`, `k8s/namespace.yaml`, `k8s/config.yaml`, `k8s/postgres.yaml`, `k8s/kafka.yaml`, `k8s/zipkin.yaml`, `k8s/prometheus.yaml`

**Interfaces:**
- Produces: ConfigMap `shop-env` with `CONFIG_SERVER_URL`, `EUREKA_URL`, `ZIPKIN_URL`, `DB_HOST`, `KAFKA_BOOTSTRAP`, `AUTH_JWKS_URI`, `JAVA_TOOL_OPTIONS` (values copied from `x-app-env` in `docker-compose.yml`); Secret `shop-secrets` with `DB_USER`, `DB_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`. Task 2 consumes both with `envFrom`.
- Produces: `scripts/k8s-check.sh`, which Task 2 extends.

- [ ] **Step 1: Write `scripts/k8s-check.sh`** (bash, `set -euo pipefail`, no cluster needed). It renders with `kubectl kustomize .` and fails with a message unless:
  - the count of each kind matches an `EXPECTED` table at the top — for this task: Namespace 1, ConfigMap 3, Secret 1, StatefulSet 1, Deployment 3, Service 4;
  - the Kafka Deployment contains `enableServiceLinks: false`;
  - every line mentioning `postgres-init` or `prometheus-config` has a hash suffix (`-[a-z0-9]{10}`).
  Prints `OK` on success.

- [ ] **Step 2: Run it** — `./scripts/k8s-check.sh` → fails (no `kustomization.yaml`).

- [ ] **Step 3: Write `kustomization.yaml`** — `namespace: shop`; `resources:` listing the six `k8s/` files of this task; `configMapGenerator` entries `postgres-init` (file `infra/postgres/init.sql`) and `prometheus-config` (file `infra/prometheus/prometheus.yml`); an `images:` block with one commented example showing `name: shop/auth-service` / `newName: registry.example.com/shop/auth-service`.

- [ ] **Step 4: Write `k8s/namespace.yaml` and `k8s/config.yaml`.** The Secret uses `stringData` with the Compose development values and a comment that they are development-only.

- [ ] **Step 5: Write `k8s/postgres.yaml`** — headless-free plain Service + StatefulSet (`serviceName: postgres`). `POSTGRES_USER`/`POSTGRES_PASSWORD` from `shop-secrets` keys `DB_USER`/`DB_PASSWORD`, `POSTGRES_DB: shop`. `volumeClaimTemplates`: `data`, `ReadWriteOnce`, 1Gi, no `storageClassName`, mounted at `/var/lib/postgresql`. ConfigMap `postgres-init` mounted at `/docker-entrypoint-initdb.d`. Readiness: exec `pg_isready -U shop -d notificationdb`, period 5s.

- [ ] **Step 6: Write `k8s/kafka.yaml`** — env copied from Compose except `KAFKA_CONTROLLER_QUORUM_VOTERS: 1@localhost:9093`; `enableServiceLinks: false`; Service exposes 9092 only; readiness: `tcpSocket` 9092. Memory request 512Mi, limit 768Mi.

- [ ] **Step 7: Write `k8s/zipkin.yaml` and `k8s/prometheus.yaml`** — Zipkin: `JAVA_OPTS: -Xmx256m`, readiness `GET /health` on 9411. Prometheus: ConfigMap `prometheus-config` mounted at `/etc/prometheus`, readiness `GET /-/ready` on 9090.

- [ ] **Step 8: Run `./scripts/k8s-check.sh`** → `OK`.

- [ ] **Step 9: Commit** — `git add kustomization.yaml k8s scripts/k8s-check.sh && git commit -m "Add Kubernetes manifests for shared config and infrastructure"`

### Task 2: The eight Spring Boot services

**Files:**
- Create: `k8s/discovery-server.yaml`, `k8s/config-server.yaml`, `k8s/auth-service.yaml`, `k8s/product-service.yaml`, `k8s/inventory-service.yaml`, `k8s/order-service.yaml`, `k8s/notification-service.yaml`, `k8s/api-gateway.yaml`
- Modify: `kustomization.yaml` (resources), `scripts/k8s-check.sh` (expected counts and new assertions)

**Interfaces:**
- Consumes: `shop-env`, `shop-secrets` from Task 1.

- [ ] **Step 1: Extend `scripts/k8s-check.sh`** — expected Deployment 11, Service 12; plus: exactly 8 containers with an image starting `shop/`; each of those has `failureThreshold: 30` with `periodSeconds: 10` on its startup probe.

- [ ] **Step 2: Run it** → fails on the Deployment count.

- [ ] **Step 3: Write the eight files**, identical in shape (Service + Deployment), differing only in name and port:
  - image `shop/<module>:0.1.0`, `imagePullPolicy: IfNotPresent`;
  - `envFrom` `shop-env` and `shop-secrets`; discovery-server and config-server instead set only `JAVA_TOOL_OPTIONS: -Xmx256m`;
  - `startupProbe` `GET /actuator/health/readiness` period 10s, failureThreshold 30; `readinessProbe` same path, period 10s; `livenessProbe` `GET /actuator/health/liveness`, period 10s;
  - resources: requests `cpu: 100m`, `memory: 384Mi`; limits `memory: 512Mi`.
  Add all eight to `kustomization.yaml`.

- [ ] **Step 4: Run `./scripts/k8s-check.sh`** → `OK`.

- [ ] **Step 5: Commit** — `git commit -m "Add Kubernetes manifests for the Spring Boot services"`

### Task 3: Image script and README

**Files:**
- Create: `scripts/k8s-images.sh`
- Modify: `README.md`, `Dockerfile:14` (comment only: curl is also unused by Kubernetes, leave the install)

- [ ] **Step 1: Write `scripts/k8s-images.sh`** — usage `k8s-images.sh [registry] [tag]`, tag default `0.1.0`. For each of the eight modules: `docker build --build-arg MODULE=$m -t $name .`, where `$name` is `shop/$m:$tag` without a registry and `$registry/shop/$m:$tag` with one; push only when a registry is given.

- [ ] **Step 2: Check it** — `bash -n scripts/k8s-images.sh` → no output.

- [ ] **Step 3: README** — add "Run on Kubernetes" after the Compose instructions: prerequisites (cluster, `kubectl`, default storage class, about 6 GB of memory); build and push; set the registry in `kustomization.yaml`; `kubectl apply -k .`; `kubectl -n shop get pods -w` with the note that restarts during first start are expected; port-forward commands; smoke test; switching the gateway to `LoadBalancer`; troubleshooting (`ImagePullBackOff`, Pending PVC); `kubectl delete -k .` and the note that the Postgres volume claim survives it. Remove "Kubernetes manifests" from the "Not included" line. Do not change the Dockerfile after all if the comment is still accurate.

- [ ] **Step 4: Run `./scripts/k8s-check.sh`** → `OK`.

- [ ] **Step 5: Commit** — `git commit -m "Add image build script and Kubernetes instructions"`

## Not verifiable here

Applying to a cluster and running `scripts/smoke-test.sh`. The authoring machine has no cluster and no running Docker daemon; a server-side or client-side `kubectl apply --dry-run` also needs a cluster to look up resource types. The final report must say so.
