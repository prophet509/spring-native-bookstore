# Business Flow: Inventory Reservation & Release

The inventory-service is event-driven. It consumes order lifecycle events and adjusts stock,
emitting a decision back to the saga. It also exposes a small REST API for manual stock management.

## Reserve stock (consumes `order-created-events`)

```mermaid
flowchart TD
    start([order-created-events]) --> consumer[OrderEventConsumer.reserveStock]
    consumer --> idem{Redis idempotency<br/>claim orderId?}
    idem -->|duplicate| reserved1[Return RESERVED decision]
    idem -->|new claim| existing{Reservation<br/>already in DB?}
    existing -->|yes| reserved2[Return RESERVED idempotent]
    existing -->|no| load[Load InventoryItems by ISBN]
    load --> check{All items present<br/>and enough stock?}
    check -->|no| reject[InsufficientStockException]
    check -->|yes| reserve[item.reserve qty for each]
    reserve --> save[saveAll items + save Reservations]
    save --> pubok[publish inventory-events RESERVED]
    reject --> pubrej[publish inventory-events REJECTED]

    pubok --> done([decision emitted])
    pubrej --> done
    reserved1 --> done
    reserved2 --> done

    consumer -. OptimisticLockingFailure .-> retry[retry x3 backoff]
    retry --> idem
    consumer -. DataIntegrityViolation .-> dup[treat as RESERVED]
    dup --> done
```

## Release stock (consumes `order-cancelled-events`)

```mermaid
flowchart TD
    start([order-cancelled-events]) --> consumer[OrderEventConsumer.releaseStock]
    consumer --> find[ReleaseStockService: find reservations by orderId]
    find --> filter{Any RESERVED<br/>reservations?}
    filter -->|none| noop[Idempotent no-op]
    filter -->|yes| load[Load InventoryItems by ISBN]
    load --> release[item.release qty for each]
    release --> save[saveAll items]
    save --> mark[mark reservations RELEASED]
    mark --> done([stock released])
    noop --> done
```

## Manual stock management (REST `/inventory`)

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant IC as InventoryController
    participant S as StockManagementService
    participant DB as PostgreSQL

    Admin->>IC: POST /inventory/{isbn}/add {quantity}
    IC->>S: addStock(isbn, qty)
    S->>DB: findByIsbn (or create empty)
    S->>DB: save adjusted item
    S-->>IC: InventoryItem
    IC-->>Admin: 200 OK

    Admin->>IC: POST /inventory/{isbn}/reduce {quantity}
    IC->>S: reduceStock(isbn, qty)
    S->>DB: findByIsbn (error if missing)
    S->>DB: save adjusted item
    S-->>IC: InventoryItem / 4xx
```

## Domain invariants (`InventoryItem`)

- `availableQuantity` and `reservedQuantity` never go negative.
- `reserve(qty)` moves stock from available → reserved (fails if insufficient).
- `release(qty)` moves stock from reserved → available.
- `adjust(delta)` changes available stock (manual add/reduce).
- Optimistic `version` guards concurrent updates.
