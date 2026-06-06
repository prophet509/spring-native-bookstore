# Business Flow: Search Indexing & Query

search-service keeps an Elasticsearch index in sync with the catalog via `book.*` events, and serves
cached full-text queries. The cache layer is a Caffeine-backed `@Primary` decorator over the
Elasticsearch adapter.

## Index sync (consumes `book.*`)

```mermaid
flowchart TD
    c1{{book.created}} --> h1[handleBookCreated] --> idx[(Elasticsearch index)]
    c2{{book.updated}} --> h2[handleBookUpdated] --> idx
    c3{{book.deleted}} --> h3[handleBookDeleted] --> rm[remove from index]
    rm --> idx
```

## Query path with cache decorator

```mermaid
flowchart TD
    req([GET /search?query=...]) --> ctrl[SearchController]
    ctrl --> svc[SearchBookService.search]
    svc --> cache[CachedSearchQueryAdapter @Primary]
    cache --> hit{Caffeine hit?<br/>60s TTL, max 5k}
    hit -->|yes| ret[Return cached SearchPage]
    hit -->|no| es[ElasticsearchSearchQueryAdapter]
    es --> esdb[(Elasticsearch)]
    esdb --> put[cache.put key] --> ret
```

## Query types

`CachedSearchQueryAdapter` decorates every query method:

| Method | Cache key prefix |
|--------|------------------|
| `searchByTitle` | `search:title:` |
| `searchByAuthor` | `search:author:` |
| `searchByPublisher` | `search:publisher:` |
| `searchByIsbn` | `search:isbn:` |
| `searchAll` | `search:all:` |
| `suggestByTitle` | `suggest:title:` |
| `suggestByAuthor` | `suggest:author:` |

Keys include page number and size so paginated results are cached independently. Suggestion fluxes
are cached with `.cache()` so the upstream ES call is shared across subscribers.
