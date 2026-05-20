#!/usr/bin/env bash
# Load test: hammer order-service with concurrent requests, then verify inventory never goes negative.
# Usage: ./scripts/load-test-orders.sh [TOTAL_REQUESTS] [CONCURRENCY]
#   Defaults: 1000 requests, 50 concurrent (for 1M use k6 script instead)
set -euo pipefail

TOTAL=${1:-1000}
CONCURRENCY=${2:-50}
ISBN="9781617296956"
ORDER_URL="http://localhost:9002/orders"
INVENTORY_URL="http://localhost:9004/inventory/${ISBN}"
KEYCLOAK_URL="http://localhost:8080/realms/PolarBookshop/protocol/openid-connect/token"

echo "=== Load Test: ${TOTAL} orders, concurrency ${CONCURRENCY} ==="

# 1. Get token
echo "[1/4] Getting access token..."
TOKEN=$(curl -sf -X POST "$KEYCLOAK_URL" \
  -d "grant_type=password" \
  -d "client_id=edge-service" \
  -d "username=bjorn" \
  -d "password=bjorn" \
  -d "scope=openid roles" | jq -r '.access_token')

if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "ERROR: Failed to get token"; exit 1
fi

# 2. Check initial stock
echo "[2/4] Checking initial inventory..."
INITIAL_STOCK=$(curl -sf "$INVENTORY_URL" | jq '.availableQuantity')
echo "  Initial available stock: ${INITIAL_STOCK}"

# 3. Fire requests
echo "[3/4] Firing ${TOTAL} POST /orders requests (concurrency=${CONCURRENCY})..."
START=$(date +%s%N)

RESULTS_FILE=$(mktemp /tmp/load-test-results.XXXXXX)
seq 1 "$TOTAL" | xargs -P "$CONCURRENCY" -I {} \
  sh -c 'curl -s -o /dev/null -w "%{http_code}\n" \
    --max-time 10 --connect-timeout 3 \
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

# 4. Results
TOTAL_SENT=$(wc -l < "$RESULTS_FILE")
SUCCESS=$(grep -c "^2[0-9][0-9]$" "$RESULTS_FILE" || true)
ERRORS=$(grep -cv "^2[0-9][0-9]$" "$RESULTS_FILE" || true)

echo ""
echo "=== Results ==="
echo "  Duration:   ${ELAPSED}ms"
echo "  Sent:       ${TOTAL_SENT}"
echo "  Success:    ${SUCCESS} (HTTP 2xx)"
echo "  Non-2xx:    ${ERRORS}"
echo "  HTTP codes: $(sort "$RESULTS_FILE" | uniq -c | tr '\n' ' ')"
echo "  RPS:        $(( TOTAL_SENT * 1000 / (ELAPSED + 1) ))"

# 5. Verify inventory
echo ""
echo "[4/4] Verifying inventory integrity..."
sleep 3  # wait for async Kafka processing
FINAL=$(curl -sf "$INVENTORY_URL" | jq '.')
AVAILABLE=$(echo "$FINAL" | jq '.availableQuantity')
RESERVED=$(echo "$FINAL" | jq '.reservedQuantity')

echo "  Final available: ${AVAILABLE}"
echo "  Final reserved:  ${RESERVED}"

if [ "$AVAILABLE" -lt 0 ]; then
  echo "  ❌ FAIL: Inventory went NEGATIVE! availableQuantity=${AVAILABLE}"
  exit 1
else
  echo "  ✅ PASS: Inventory never went negative"
fi

EXPECTED_RESERVED=$((INITIAL_STOCK - AVAILABLE))
echo "  Stock consumed: ${EXPECTED_RESERVED} (initial ${INITIAL_STOCK} - available ${AVAILABLE})"
echo "  Reserved field: ${RESERVED}"

rm -f "$RESULTS_FILE"
