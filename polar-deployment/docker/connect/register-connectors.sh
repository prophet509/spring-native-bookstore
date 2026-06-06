#!/usr/bin/env bash
# Registers the Debezium outbox connectors with Kafka Connect.
# Prereq: `docker compose up -d` (polar-kafka-connect healthy on :8083).
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "Waiting for Kafka Connect at ${CONNECT_URL} ..."
until curl -sf "${CONNECT_URL}/connectors" >/dev/null; do sleep 2; done

for cfg in order-outbox-connector inventory-outbox-connector catalog-outbox-connector; do
  echo "Registering ${cfg} ..."
  curl -sf -X PUT \
    -H "Content-Type: application/json" \
    --data "$(jq '.config' "${DIR}/${cfg}.json")" \
    "${CONNECT_URL}/connectors/${cfg}/config" >/dev/null
  echo "  ok"
done

echo "Done. Status: ${CONNECT_URL}/connectors?expand=status"
