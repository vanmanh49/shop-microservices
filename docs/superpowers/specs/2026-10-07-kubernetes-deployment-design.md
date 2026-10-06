# Kubernetes deployment — design

## Goal

Run the whole shop stack on any Kubernetes cluster (local or cloud) with
`kubectl apply -k .`, using only what ships with `kubectl`. No Java code changes: the
services are already configured entirely through environment variables.

Success: on a cluster with a default storage class, every pod becomes Ready and
`scripts/smoke-test.sh` passes against a port-forward to the gateway.

## Approach

Plain manifests assembled with Kustomize. Eureka and the config server stay, so the
patterns the project demonstrates are unchanged. Kubernetes Services carry the same names
as the Compose services, so every existing URL (`http://config-server:8888`,
`kafka:9092`, the Prometheus targets) works as is.

Rejected: a Helm chart (extra tool, templating for options nobody asked for) and replacing
Eureka/config server with Kubernetes-native discovery (code changes, removes the patterns).

## Files

| File | Contents |
|---|---|
| `kustomization.yaml` (repo root) | Namespace `shop`, resource list, `images:` block, ConfigMaps generated from `infra/postgres/init.sql` and `infra/prometheus/prometheus.yml` |
| `k8s/namespace.yaml` | Namespace `shop` |
| `k8s/config.yaml` | ConfigMap `shop-env` (URLs, `DB_HOST`, `KAFKA_BOOTSTRAP`, `JAVA_TOOL_OPTIONS`) and Secret `shop-secrets` (`DB_USER`, `DB_PASSWORD`, `ADMIN_USERNAME`, `ADMIN_PASSWORD`) |
| `k8s/postgres.yaml` | StatefulSet + Service |
| `k8s/kafka.yaml`, `k8s/zipkin.yaml`, `k8s/prometheus.yaml` | Deployment + Service each |
| `k8s/<service>.yaml` × 8 | Deployment + Service for each Spring Boot module |
| `scripts/k8s-images.sh` | Builds and pushes the 8 images to a registry given as argument |
| `README.md` | New "Run on Kubernetes" section; remove "Kubernetes manifests" from "Not included" |

The kustomization sits at the repo root because Kustomize only reads files at or below its
own directory, and the two `infra/` files should have a single copy shared with Compose.

## Workloads

**Spring Boot services** (discovery-server, config-server, auth, product, inventory,
order, notification, api-gateway): one Deployment with 1 replica each.

- Image `shop/<module>:0.1.0`, rewritten to the user's registry by the `images:` block.
- Environment from `shop-env` and `shop-secrets` (discovery-server and config-server only
  need `JAVA_TOOL_OPTIONS`).
- Probes on the actuator port: startup `/actuator/health/readiness` (up to 5 minutes),
  readiness `/actuator/health/readiness`, liveness `/actuator/health/liveness`. Spring
  Boot enables these endpoints automatically when it detects Kubernetes.
- Memory request 384Mi, limit 512Mi (heap is capped at 256m); CPU request 100m, no limit.
- Service of type `ClusterIP` on the module's port.

**Postgres**: StatefulSet, 1 replica, `postgres:18.6`, a 1Gi `volumeClaimTemplate` on the
default storage class mounted at `/var/lib/postgresql`, `init.sql` mounted into
`/docker-entrypoint-initdb.d`, readiness via `pg_isready -d notificationdb`.

**Kafka**: Deployment, 1 replica, `apache/kafka:4.3.1`, same KRaft settings as Compose
with two changes: the controller quorum voter is `1@localhost:9093` (the Service does not
route to a pod that is not Ready yet), and `enableServiceLinks: false` on the pod, because
a Service named `kafka` makes Kubernetes inject `KAFKA_PORT=tcp://…`, which the image
would read as a broker setting. No persistent storage, as in Compose.

**Zipkin, Prometheus**: Deployments with the Compose images; Prometheus mounts the
generated ConfigMap.

## Startup order

`depends_on` has no equivalent. A service that starts before the config server, Postgres
or Kafka exits and is restarted by Kubernetes until its dependencies are up. First start
may show a few restarts; this is expected.

## Access

Every Service is `ClusterIP`. Reach the stack with
`kubectl -n shop port-forward svc/api-gateway 8080:8080` (likewise 8761, 9411, 9090).
This works on every provider and keeps the development admin password off a public load
balancer. The README shows the one-line change to `type: LoadBalancer`.

## Images

`scripts/k8s-images.sh <registry> [tag]` runs `docker build --build-arg MODULE=<m>` and
`docker push` for each module. The user sets the same registry in `kustomization.yaml`.
On a local cluster that shares the Docker image store, build without pushing and leave
the names as they are.

## Credentials

Same development values as Compose, in a Secret marked development-only.

## Verification

- Here: `kubectl kustomize .` renders and `scripts/k8s-check.sh` asserts on the output. A dry run needs a cluster.
- On a cluster: all pods Ready, then `scripts/smoke-test.sh` through the port-forward.
  Not possible on the authoring machine (no cluster, Docker not running).

## Out of scope

Ingress, autoscaling, network policies, more than one replica, per-environment overlays,
persistent Kafka storage, a shared JWT signing key (needed before running two
`auth-service` replicas).
