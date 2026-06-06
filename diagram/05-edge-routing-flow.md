# Business Flow: Edge Routing, Auth & Resilience

edge-service is the single entry point. It authenticates users via Keycloak (OAuth2 authorization
code), stores the session in Redis, relays the access token downstream, and protects each route with
a circuit breaker, retries, and a per-user rate limiter.

## Authenticated request

```mermaid
sequenceDiagram
    autonumber
    actor U as Browser
    participant E as edge-service
    participant KC as Keycloak
    participant R as Redis
    participant Svc as downstream service

    U->>E: GET /books
    alt no session
        E->>KC: redirect to login (authorization_code)
        KC-->>U: login page
        U->>KC: credentials
        KC-->>E: code → exchange for tokens
        E->>R: SaveSession (WebSession)
    end
    E->>R: rate-limit check (userKeyResolver)
    alt over limit
        E-->>U: 429 Too Many Requests
    else allowed
        E->>Svc: forward + TokenRelay (Bearer JWT)
        Note over E,Svc: CircuitBreaker per route
        alt downstream healthy
            Svc-->>E: 200 + body
            E-->>U: 200 (Cache-Control: max-age=30)
        else downstream failing/slow
            E->>E: fallback (forward:/catalog-fallback)
            E-->>U: 200 empty / degraded
        end
    end
```

## Routes & resilience

```mermaid
flowchart LR
    subgraph edge[edge-service :9000]
        rl[RequestRateLimiter<br/>Redis, 10 rps / burst 20]
        cb[CircuitBreaker + Retry]
    end

    client --> rl --> cb
    cb -->|/books, /books/**| catalog[catalog-service :9001]
    cb -->|/orders, /orders/**| order[order-service :9002]
    cb -->|/inventory/**| inventory[inventory-service :9004]
    cb -->|/search/**| search[search-service :9005]
    cb -->|/, *.css, *.js| spa[SPA]

    cb -.fallback.-> fb["/catalog-fallback<br/>/order-fallback<br/>/inventory-fallback<br/>/search-fallback"]
```

## Mechanisms

| Concern | Mechanism | Config |
|---------|-----------|--------|
| AuthN | OAuth2 login (Keycloak), `TokenRelay` | `edge-service.yml` |
| Session | Spring Session in Redis (`polar:edge`, 7d) | `spring.session` |
| Rate limit | Redis RequestRateLimiter keyed by user | 10 rps, burst 20 |
| Circuit breaker | Resilience4j per route | sliding window 20, 50% threshold |
| Retry | GET only, 3 attempts, exp backoff | `default-filters` |
| Caching hint | `Cache-Control: max-age=30` on catalog & search | `SetResponseHeader` |

> Note: downstream domain services act as OAuth2 **resource servers** — they independently validate
> the relayed JWT against Keycloak, so security is not solely enforced at the edge.
