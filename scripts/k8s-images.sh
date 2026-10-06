#!/usr/bin/env bash
# Builds the eight service images for Kubernetes, and pushes them when a registry is given.
# Usage: ./scripts/k8s-images.sh                        (local cluster sharing Docker's images)
#        ./scripts/k8s-images.sh registry.example.com   (build and push)
#        PLATFORM=linux/amd64 ./scripts/k8s-images.sh registry.example.com
#                                                       (when the nodes' CPU differs from this machine's)
set -euo pipefail
cd "$(dirname "$0")/.."

REGISTRY="${1:-}"

for module in discovery-server config-server auth-service product-service \
              inventory-service order-service notification-service api-gateway; do
  image="${REGISTRY:+$REGISTRY/}shop/$module:0.1.0"
  docker build ${PLATFORM:+--platform "$PLATFORM"} --build-arg MODULE="$module" -t "$image" .
  [[ -z "$REGISTRY" ]] || docker push "$image"
done
