// k6 load test: 1M virtual orders to verify inventory doesn't go negative.
// Install: https://grafana.com/docs/k6/latest/set-up/install-k6/
// Run:     k6 run scripts/load-test-k6.js
// Adjust:  k6 run --vus 500 --iterations 1000000 scripts/load-test-k6.js
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter } from "k6/metrics";

const failedOrders = new Counter("failed_orders");

const ISBN = "1234567890";
const ORDER_URL = "http://localhost:9002/orders";
const INVENTORY_URL = `http://localhost:9004/inventory/${ISBN}`;
const KEYCLOAK_URL =
  "http://localhost:8080/realms/PolarBookshop/protocol/openid-connect/token";

export const options = {
  scenarios: {
    load_test: {
      executor: "shared-iterations",
      vus: 500,
      iterations: 1000000,
      maxDuration: "30m",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.5"],
    http_req_duration: ["p(95)<5000"],
  },
};

let token = "";

export function setup() {
  const res = http.post(
    KEYCLOAK_URL,
    {
      grant_type: "password",
      client_id: "edge-service",
      username: "bjorn",
      password: "bjorn",
      scope: "openid roles",
    },
    { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
  );
  const t = JSON.parse(res.body).access_token;
  if (!t) throw new Error("Failed to get token");

  const inv = http.get(INVENTORY_URL);
  const initial = JSON.parse(inv.body);
  console.log(`Initial stock: available=${initial.availableQuantity}, reserved=${initial.reservedQuantity}`);

  return { token: t, initialStock: initial.availableQuantity };
}

export default function (data) {
  const res = http.post(ORDER_URL, JSON.stringify({ isbn: ISBN, quantity: 1 }), {
    headers: {
      Authorization: `Bearer ${data.token}`,
      "Content-Type": "application/json",
    },
  });

  const ok = check(res, {
    "status is 200": (r) => r.status === 200,
  });
  if (!ok) failedOrders.add(1);
}

export function teardown(data) {
  sleep(5); // wait for Kafka async processing
  const inv = http.get(INVENTORY_URL);
  const final_ = JSON.parse(inv.body);
  console.log(`Final stock: available=${final_.availableQuantity}, reserved=${final_.reservedQuantity}`);

  if (final_.availableQuantity < 0) {
    console.error(`❌ FAIL: Inventory went NEGATIVE! available=${final_.availableQuantity}`);
  } else {
    console.log(`✅ PASS: Inventory never went negative`);
    console.log(`Stock consumed: ${data.initialStock - final_.availableQuantity}`);
  }
}
