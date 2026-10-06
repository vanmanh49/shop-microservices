#!/usr/bin/env bash
# Offline check of the Kubernetes manifests: renders them and asserts on the result.
# Usage: ./scripts/k8s-check.sh             (needs kubectl; no cluster required)
set -euo pipefail
cd "$(dirname "$0")/.."

# kind:count pairs the rendered output must contain.
EXPECTED="Namespace:1 ConfigMap:3 Secret:1 StatefulSet:1 Deployment:3 Service:4"

fail() { echo "FAIL  $1"; exit 1; }

RENDERED="$(kubectl kustomize .)" || fail "kubectl kustomize . did not render"

for pair in $EXPECTED; do
  kind="${pair%%:*}" want="${pair##*:}"
  got="$(grep -c "^kind: $kind\$" <<<"$RENDERED" || true)"
  [[ "$got" == "$want" ]] || fail "expected $want $kind, found $got"
done

# A Service named kafka makes Kubernetes inject KAFKA_PORT, which the Kafka image
# would read as a broker setting.
grep -q 'enableServiceLinks: false' <<<"$RENDERED" || fail "kafka pod must set enableServiceLinks: false"

# Generated ConfigMaps get a hash suffix; a reference without one mounts nothing.
if grep -E 'postgres-init|prometheus-config' <<<"$RENDERED" | grep -vqE '(postgres-init|prometheus-config)-[a-z0-9]{10}'; then
  fail "a generated ConfigMap is referenced without its hash suffix"
fi

echo OK
