#!/usr/bin/env bash
# Stress test through edge gateway (port 9000): hammer POST /orders, then verify inventory never
# goes negative AND report HTTP code distribution. Goes through the FULL gateway stack:
# rate-limiter -> circuit-breaker -> TokenRelay -> order-service -> outbox -> Debezium -> inventory.
#
# Usage:
#   ./scripts/load-test-orders-edge.sh [TOTAL] [CONCURRENCY]
# Defaults: TOTAL=1000, CONCURRENCY=50
#
# NOTE: edge-service has a per-user RequestRateLimiter (10 r/s replenish, 20 burst). With a single
# user pushing thousands of concurrent requests you WILL see 429s — that is the gateway protecting
# the backend, not a defect.
set -euo pipefail

TOTAL=${1:-1000}
CONCURRENCY=${2:-50}
ISBN="${ISBN:-9781617296956}"

EDGE_BASE="${EDGE_BASE:-http://localhost:9000}"
ORDER_URL="${EDGE_BASE}/orders"
INVENTORY_URL="http://localhost:9004/inventory/${ISBN}"

KEYCLOAK_NETWORK="${KEYCLOAK_NETWORK:-polar-network}"
KEYCLOAK_HOSTPORT="${KEYCLOAK_HOSTPORT:-polar-keycloak:8080}"
KEYCLOAK_URL="http://${KEYCLOAK_HOSTPORT}/realms/PolarBookshop/protocol/openid-connect/token"

USER_NAME="${USER_NAME:-bjorn}"
USER_PASS="${USER_PASS:-bjorn}"

echo "=== Edge Stress Test ==="
echo "  Endpoint:    ${ORDER_URL}"
echo "  Total:       ${TOTAL} requests"
echo "  Concurrency: ${CONCURRENCY}"
echo "  User:        ${USER_NAME}"
echo ""

# 1. Get token in-network so iss=http://polar-keycloak:8080/... matches resource-server config.
echo "[1/5] Getting access token (in-network via ${KEYCLOAK_HOSTPORT})..."
TOKEN="${TOKEN:-$(docker run --rm --network "$KEYCLOAK_NETWORK" curlimages/curl:latest \
  -sf -X POST "$KEYCLOAK_URL" \
  -d "grant_type=password" \
  -d "client_id=edge-service" \
  -d "username=${USER_NAME}" \
  -d "password=${USER_PASS}" \
  -d "scope=openid roles" | jq -r '.access_token')}"

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "ERROR: Failed to get token"; exit 1
fi
echo "  Token acquired (first 40 chars: ${TOKEN:0:40}...)"

# 2. Sanity check via edge with the token
echo "[2/5] Sanity check: POST /orders via edge with Bearer..."
SANITY_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$ORDER_URL" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"isbn\":\"${ISBN}\",\"quantity\":1}")
if ! [[ "$SANITY_CODE" =~ ^2[0-9][0-9]$ ]]; then
  echo "  ❌ Sanity check failed: HTTP ${SANITY_CODE} (expected 2xx)"; exit 1
fi
echo "  ✅ Edge accepts Bearer (HTTP ${SANITY_CODE})"

# 3. Capture initial inventory
echo "[3/5] Initial inventory..."
INITIAL_STOCK=$(curl -sf "$INVENTORY_URL" | jq '.availableQuantity')
INITIAL_RESERVED=$(curl -sf "$INVENTORY_URL" | jq '.reservedQuantity')
INITIAL_VERSION=$(curl -sf "$INVENTORY_URL" | jq '.version')
echo "  available=${INITIAL_STOCK}, reserved=${INITIAL_RESERVED}, version=${INITIAL_VERSION}"

# 4. Fire requests
echo "[4/5] Firing ${TOTAL} POST /orders via edge (concurrency=${CONCURRENCY})..."
START=$(date +%s%N)

RESULTS_FILE=$(mktemp /tmp/edge-load-test-results.XXXXXX)
seq 1 "$TOTAL" | xargs -P "$CONCURRENCY" -I {} \
  sh -c 'curl -s -o /dev/null -w "%{http_code}\n" \
    --max-time 15 --connect-timeout 3 \
    -X POST "$0" \
    -H "Authorization: Bearer $1" \
    -H "Content-Type: application/json" \
    -d "{\"isbn\":\"$2\",\"quantity\":1}" \
    >> "$3"' "$ORDER_URL" "$TOKEN" "$ISBN" "$RESULTS_FILE" &

XARGS_PID=$!
while kill -0 "$XARGS_PID" 2>/dev/null; do
  DONE=$(wc -l < "$RESULTS_FILE" 2>/dev/null || echo 0)
  printf "\r  Progress: %d/%d (%d%%)" "$DONE" "$TOTAL" "$((DONE * 100 / TOTAL))"
  sleep 0.5
done
wait "$XARGS_PID" || true
printf "\r  Progress: %d/%d (100%%)\n" "$TOTAL" "$TOTAL"

END=$(date +%s%N)
ELAPSED=$(( (END - START) / 1000000 ))

# 5. Results
TOTAL_SENT=$(wc -l < "$RESULTS_FILE")
SUCCESS=$(grep -c "^2[0-9][0-9]$" "$RESULTS_FILE" || true)
RATE_LIMITED=$(grep -c "^429$" "$RESULTS_FILE" || true)
SERVER_ERR=$(grep -cE "^5[0-9][0-9]$" "$RESULTS_FILE" || true)
CLIENT_ERR=$(grep -cE "^4[0-9][0-9]$" "$RESULTS_FILE" || true)
ZERO=$(grep -c "^000$" "$RESULTS_FILE" || true)

echo ""
echo "=== Results ==="
printf "  Duration:    %d ms (%.2fs)\n" "$ELAPSED" "$(echo "scale=2; $ELAPSED/1000" | bc)"
echo "  Sent:        ${TOTAL_SENT}"
echo "  HTTP 2xx:    ${SUCCESS}"
echo "  HTTP 429:    ${RATE_LIMITED} (gateway rate-limiter)"
echo "  HTTP 4xx:    ${CLIENT_ERR}"
echo "  HTTP 5xx:    ${SERVER_ERR}"
echo "  HTTP 000:    ${ZERO} (timeout/conn refused)"
printf "  RPS:         %d\n" "$(( TOTAL_SENT * 1000 / (ELAPSED + 1) ))"
echo "  Distribution:"
sort "$RESULTS_FILE" | uniq -c | awk '{printf "    %s -> %d\n", $2, $1}'

# 6. Verify inventory
echo ""
echo "[5/5] Verifying inventory integrity (waiting 8s for async outbox->Debezium->Kafka->consumer)..."
sleep 8
FINAL=$(curl -sf "$INVENTORY_URL")
AVAILABLE=$(echo "$FINAL" | jq '.availableQuantity')
RESERVED=$(echo "$FINAL" | jq '.reservedQuantity')
VERSION=$(echo "$FINAL" | jq '.version')

echo "  Final: available=${AVAILABLE}, reserved=${RESERVED}, version=${VERSION}"
echo "  Δ available: $((INITIAL_STOCK - AVAILABLE))"
echo "  Δ version:   $((VERSION - INITIAL_VERSION))"

if [ "$AVAILABLE" -lt 0 ]; then
  echo "  ❌ FAIL: Inventory went NEGATIVE!"
  rm -f "$RESULTS_FILE"
  exit 1
else
  echo "  ✅ PASS: availableQuantity >= 0 (no oversell)"
fi

# 7. Verify outbox row count delta
echo ""
echo "[bonus] Outbox events written in polardb_order:"
OUTBOX_COUNT=$(docker exec -e PGPASSWORD=password polar-postgres-order \
  psql -U user -d polardb_order -tA -c \
  "select count(*) from outbox_event where destination='order-created-events';" 2>/dev/null || echo "?")
echo "  outbox_event[destination='order-created-events'] = ${OUTBOX_COUNT}"

rm -f "$RESULTS_FILE"
