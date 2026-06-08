# Business Flow: Order Saga (happy path + rejection)

This is the core distributed transaction. An order is submitted synchronously, then a choreographed
saga across order → inventory → dispatcher coordinates stock reservation and fulfillment over Kafka.

## Sequence

```mermaid
sequenceDiagram
    autonumber
    actor U as Client
    participant E as edge-service
    participant O as order-service
    participant C as catalog-service
    participant K as Kafka
    participant I as inventory-service
    participant D as dispatcher-service

    U->>E: POST /orders {isbn, quantity}
    E->>O: forward (TokenRelay, Idempotency-Key)
    Note over O: IdempotencyWebFilter checks Redis claim
    O->>C: GET /books/{isbn} (load book)
    alt book exists
        C-->>O: BookSnapshot
        O->>O: SubmitOrderService → Order(PENDING) saved
        O->>K: publish order-created-events
        O-->>U: 201 Created (status=PENDING)
    else book missing
        C-->>O: 404 / empty
        O->>O: Order(REJECTED) saved
        O-->>U: 201 Created (status=REJECTED)
    end

    K-->>I: reserveStock(order-created-events)
    Note over I: ReserveStockService<br/>Redis idempotency claim
    alt stock available
        I->>I: reserve stock + save Reservation
        I->>K: publish inventory-events (RESERVED)
    else insufficient stock
        I->>K: publish inventory-events (REJECTED)
    end

    K-->>O: handleInventoryDecision(inventory-events)
    Note over O: ProcessInventoryDecisionService<br/>ignores non-PENDING (idempotent)
    alt RESERVED
        O->>O: order.accept() saved
        O->>K: publish order-accepted
    else REJECTED
        O->>O: order.reject() saved
    end

    K-->>D: pack(order-accepted)
    D->>D: pack → label
    D->>K: publish order-dispatched

    K-->>O: dispatchOrder(order-dispatched)
    O->>O: MarkOrderDispatchedService → order.markDispatched() saved
```

## State machine (Order)

```mermaid
stateDiagram-v2
    [*] --> PENDING: submit (book found)
    [*] --> REJECTED: submit (book not found)
    PENDING --> ACCEPTED: inventory RESERVED
    PENDING --> REJECTED: inventory REJECTED
    ACCEPTED --> DISPATCHED: order-dispatched
    DISPATCHED --> [*]
    REJECTED --> [*]
```

## Kafka topics in this flow

| Topic | Producer | Consumer | Binding (function) |
|-------|----------|----------|--------------------|
| `order-created-events` | order-service | inventory-service | `reserveStock-in-0` |
| `inventory-events` | inventory-service | order-service | `handleInventoryDecision-in-0` |
| `order-accepted` | order-service | dispatcher-service | `pack` |
| `order-dispatched` | dispatcher-service | order-service | `dispatchOrder-in-0` |

## Idempotency & resilience notes

- **HTTP idempotency** (order-service `IdempotencyWebFilter`): a client `Idempotency-Key` is claimed
  in Redis (`setIfAbsent`). Duplicate POSTs replay the cached response instead of re-creating an order.
- **Reservation idempotency** (inventory-service `ReserveStockService`): the order id is claimed in
  Redis; a duplicate `order-created` event short-circuits to a `RESERVED` decision. The DB unique
  constraint on reservations is the backstop (`DataIntegrityViolationException` → treated as reserved).
- **Optimistic locking**: `reserveStock` retries up to 3× with backoff on
  `OptimisticLockingFailureException` (concurrent stock updates).
- **Duplicate decisions**: `ProcessInventoryDecisionService` ignores decisions for orders not in
  `PENDING`, making the inventory→order step idempotent.
