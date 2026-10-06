#!/usr/bin/env bash
# Offline check of the Kubernetes manifests: renders them and asserts on the result.
# Usage: ./scripts/k8s-check.sh             (needs kubectl; no cluster required)
set -euo pipefail
cd "$(dirname "$0")/.."

# kind:count pairs the rendered output must contain.
EXPECTED="Namespace:1 ConfigMap:3 Secret:1 StatefulSet:1 Deployment:11 Service:12"

fail() { echo "FAIL  $1"; exit 1; }

RENDERED="$(kubectl kustomize .)" || fail "kubectl kustomize . did not render"

for pair in $EXPECTED; do
  kind="${pair%%:*}" want="${pair##*:}"
  got="$(grep -c "^kind: $kind\$" <<<"$RENDERED" || true)"
  [[ "$got" == "$want" ]] || fail "expected $want $kind, found $got"
done

# A Service named kafka makes Kubernetes inject KAFKA_PORT, which the Kafka image
# would read as a broker setting.
grep -q 'enableServiceLinks: false' k8s/kafka.yaml || fail "kafka pod must set enableServiceLinks: false"

# Generated ConfigMaps get a hash suffix; a reference without one mounts nothing.
if grep -E 'postgres-init|prometheus-config' <<<"$RENDERED" | grep -vqE '(postgres-init|prometheus-config)-[a-z0-9]{10}'; then
  fail "a generated ConfigMap is referenced without its hash suffix"
fi

# The eight Spring Boot services, each with five minutes to start before liveness applies.
images="$(grep -cE '^ +image: shop/' <<<"$RENDERED" || true)"
[[ "$images" == 8 ]] || fail "expected 8 shop/ images, found $images"
startup="$(grep -A6 -E '^ +startupProbe:' <<<"$RENDERED")"
for setting in 'failureThreshold: 30' 'periodSeconds: 10'; do
  slow="$(grep -c "$setting" <<<"$startup" || true)"
  [[ "$slow" == 8 ]] || fail "expected 8 startup probes with $setting, found $slow"
done

# The default probe timeout of one second is too short for a busy node.
timeouts="$(grep -c 'timeoutSeconds: 5' <<<"$RENDERED" || true)"
[[ "$timeouts" == 28 ]] || fail "expected all 28 probes to have timeoutSeconds: 5, found $timeouts"
# Heap is capped at 256m; the rest of the limit is for metaspace, code cache and threads.
roomy="$(grep -c 'memory: 768Mi' <<<"$RENDERED" || true)"
[[ "$roomy" == 9 ]] || fail "expected the 8 services and kafka to have a 768Mi memory limit, found $roomy"

echo OK
