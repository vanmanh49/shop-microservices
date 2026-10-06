#!/usr/bin/env bash
# End-to-end check of the running stack through the gateway.
# Usage: ./scripts/smoke-test.sh            (needs curl and jq)
#        BASE_URL=http://host:8080 ./scripts/smoke-test.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_USERNAME="${ADMIN_USERNAME:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin12345}"

STATUS=""
BODY=""
RESPONSE_FILE="$(mktemp)"
trap 'rm -f "$RESPONSE_FILE"' EXIT

# call METHOD PATH [TOKEN] [JSON_BODY] -> sets STATUS and BODY
call() {
  local method="$1" path="$2" token="${3:-}" data="${4:-}"
  local args=(-s -o "$RESPONSE_FILE" -w '%{http_code}' -X "$method" "$BASE_URL$path")
  [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
  [[ -n "$data" ]] && args+=(-H 'Content-Type: application/json' -d "$data")
  STATUS="$(curl "${args[@]}" || true)"
  BODY="$(cat "$RESPONSE_FILE" 2>/dev/null || true)"
}

pass() { echo "PASS  $1"; }

fail() {
  echo "FAIL  $1"
  echo "      status: $STATUS"
  echo "      body:   $BODY"
  exit 1
}

# expect DESCRIPTION EXPECTED_STATUS [JQ_FILTER EXPECTED_VALUE]
expect() {
  local description="$1" expected_status="$2" filter="${3:-}" expected_value="${4:-}"
  [[ "$STATUS" == "$expected_status" ]] || fail "$description (expected status $expected_status)"
  if [[ -n "$filter" ]]; then
    local actual
    actual="$(jq -r "$filter" <<<"$BODY" 2>/dev/null || true)"
    [[ "$actual" == "$expected_value" ]] || fail "$description (expected $filter = $expected_value, got $actual)"
  fi
  pass "$description"
}

# poll_notification TOKEN TYPE ORDER_ID -> waits up to 30s for the notification
poll_notification() {
  local token="$1" type="$2" order_id="$3"
  for _ in $(seq 1 30); do
    call GET /api/notifications "$token"
    if [[ "$STATUS" == "200" ]] && jq -e --arg type "$type" --argjson id "$order_id" \
        'any(.[]; .type == $type and .orderId == $id)' <<<"$BODY" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  return 1
}

echo "Waiting for the gateway and its routes at $BASE_URL ..."
for attempt in $(seq 1 60); do
  call GET /api/products
  [[ "$STATUS" == "200" ]] && break
  [[ "$attempt" == "60" ]] && fail "gateway did not become ready within 2 minutes"
  sleep 2
done

SUFFIX="$(date +%s)$RANDOM"
USERNAME="smoke$SUFFIX"

# 1. Registration
call POST /api/auth/register "" "{\"username\":\"$USERNAME\",\"password\":\"password123\",\"email\":\"$USERNAME@example.com\"}"
expect "1. register a user" 201 .username "$USERNAME"

# 2. Login as the user and as the admin
call POST /api/auth/login "" "{\"username\":\"$USERNAME\",\"password\":\"password123\"}"
expect "2a. log in as the user" 200 .tokenType Bearer
USER_TOKEN="$(jq -r .accessToken <<<"$BODY")"
call POST /api/auth/login "" "{\"username\":\"$ADMIN_USERNAME\",\"password\":\"$ADMIN_PASSWORD\"}"
expect "2b. log in as the admin" 200 .tokenType Bearer
ADMIN_TOKEN="$(jq -r .accessToken <<<"$BODY")"

# 3. Only an admin may create products
PRODUCT="{\"sku\":\"SMOKE-$SUFFIX\",\"name\":\"Smoke test keyboard\",\"description\":\"Created by smoke-test.sh\",\"price\":49.99}"
call POST /api/products "$USER_TOKEN" "$PRODUCT"
expect "3a. a normal user cannot create a product" 403
call POST /api/products "$ADMIN_TOKEN" "$PRODUCT"
expect "3b. the admin creates a product" 201 .price 49.99
PRODUCT_ID="$(jq -r .id <<<"$BODY")"

# 4. Stock
call PUT "/api/inventory/$PRODUCT_ID" "$ADMIN_TOKEN" '{"quantity":5}'
expect "4. the admin sets stock to 5" 200 .available 5

# 5. Public catalog
call GET "/api/products/$PRODUCT_ID"
expect "5. anyone can read a product" 200 .name "Smoke test keyboard"

# 6. Orders need a token
call POST /api/orders "" "{\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":1}]}"
expect "6. ordering without a token is rejected" 401

# 7. Place an order
call POST /api/orders "$USER_TOKEN" "{\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":2}]}"
expect "7a. the user orders 2 units" 201 .status CONFIRMED
ORDER_ID="$(jq -r .id <<<"$BODY")"
[[ "$(jq -r .total <<<"$BODY")" == "99.98" ]] || fail "7a. order total is 99.98"
call GET "/api/inventory/$PRODUCT_ID" "$USER_TOKEN"
expect "7b. stock dropped to 3" 200 .available 3

# 8. Not enough stock
call POST /api/orders "$USER_TOKEN" "{\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":99}]}"
expect "8a. ordering 99 units is rejected" 409
call GET "/api/inventory/$PRODUCT_ID" "$USER_TOKEN"
expect "8b. stock is still 3" 200 .available 3

# 9. The order event reaches notification-service through Kafka
if poll_notification "$USER_TOKEN" ORDER_PLACED "$ORDER_ID"; then
  pass "9. an ORDER_PLACED notification arrived"
else
  fail "9. an ORDER_PLACED notification arrived (waited 30s)"
fi

# 10. Cancel
call POST "/api/orders/$ORDER_ID/cancel" "$USER_TOKEN"
expect "10a. the user cancels the order" 200 .status CANCELLED
call GET "/api/inventory/$PRODUCT_ID" "$USER_TOKEN"
expect "10b. stock is back to 5" 200 .available 5
if poll_notification "$USER_TOKEN" ORDER_CANCELLED "$ORDER_ID"; then
  pass "10c. an ORDER_CANCELLED notification arrived"
else
  fail "10c. an ORDER_CANCELLED notification arrived (waited 30s)"
fi

# 11. Internal endpoints stay internal
call POST /api/inventory/reservations "$ADMIN_TOKEN" "{\"orderRef\":\"smoke\",\"items\":[{\"productId\":$PRODUCT_ID,\"quantity\":1}]}"
expect "11. reservations cannot be made through the gateway" 403

echo
echo "All checks passed."
