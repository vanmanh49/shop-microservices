#!/usr/bin/env bash
# Builds the eight service images for Kubernetes, and pushes them when a registry is given.
# Usage: ./scripts/k8s-images.sh                              (local cluster sharing Docker's images)
#        ./scripts/k8s-images.sh registry.example.com [tag]   (build and push; tag defaults to 0.1.0)
set -euo pipefail
cd "$(dirname "$0")/.."

REGISTRY="${1:-}"
TAG="${2:-0.1.0}"

for module in discovery-server config-server auth-service product-service \
              inventory-service order-service notification-service api-gateway; do
  image="${REGISTRY:+$REGISTRY/}shop/$module:$TAG"
  docker build --build-arg MODULE="$module" -t "$image" .
  [[ -z "$REGISTRY" ]] || docker push "$image"
done
