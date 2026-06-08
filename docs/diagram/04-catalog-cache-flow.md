# Business Flow: Catalog CRUD, Caching & Event Publication

catalog-service is the source of truth for books. Reads are cached (Caffeine + Redis composite),
and every mutation publishes a `book.*` event that search-service and order-service consume.

## Read path with cache

```mermaid
flowchart TD
    req([GET /books/isbn]) --> ctrl[BookController]
    ctrl --> svc[BookCatalogService.viewBookDetail]
    svc --> repo["BookRepositoryImpl.findByIsbn<br/>@Cacheable(books, key=isbn)"]
    repo --> hit{Cache hit?}
    hit -->|yes| ret[Return cached Book]
    hit -->|no| db[(PostgreSQL)]
    db --> store[Store in cache] --> ret
```

The cache is a `CompositeCacheManager`: a local Caffeine layer (`books`, `booksPage`,
10 min TTL, max 10k) backed by a Redis layer (10 min TTL, `booksPage` 2 min TTL).

## Write path with cache eviction + event

```mermaid
sequenceDiagram
    autonumber
    actor Admin
    participant BC as BookController
    participant S as BookCatalogService
    participant R as BookRepositoryImpl
    participant Cache as Caffeine/Redis
    participant DB as PostgreSQL
    participant K as Kafka

    Admin->>BC: POST /books (create)
    BC->>S: addBookToCatalog(book)
    S->>R: existsByIsbn (cache-aware)
    alt already exists
        S-->>BC: 409 BookAlreadyExistsException
    else new
        S->>R: save(book)
        R->>DB: insert
        R->>Cache: @CachePut books, evict booksPage
        S->>K: publishBookCreated → book.created
        S-->>BC: 201 Created
    end

    Admin->>BC: PUT /books/{isbn} (update)
    BC->>S: editBookDetails(book)
    S->>R: save(updated) → @CachePut + evict booksPage
    S->>K: publishBookUpdated → book.updated

    Admin->>BC: DELETE /books/{isbn}
    BC->>S: deleteBook(isbn)
    S->>R: deleteByIsbn → @CacheEvict books + booksPage
    S->>K: publishBookDeleted → book.deleted
```

## Event fan-out (`book.*`)

```mermaid
flowchart LR
    catalog[catalog-service] -->|book.created| t1{{book.created}}
    catalog -->|book.updated| t2{{book.updated}}
    catalog -->|book.deleted| t3{{book.deleted}}

    t1 --> search[search-service<br/>handleBookCreated → index]
    t2 --> search2[search-service<br/>handleBookUpdated → reindex]
    t3 --> search3[search-service<br/>handleBookDeleted → remove]

    t1 --> order[order-service<br/>BookEventConsumer → snapshot upsert]
    t2 --> order
    t3 --> order
```

## Cache annotations (`BookRepositoryImpl`)

| Method | Annotation | Effect |
|--------|-----------|--------|
| `findByIsbn` | `@Cacheable(books, key=#isbn)` | cache single book |
| `findAll` | `@Cacheable(booksPage, key=methodName)` | cache list page |
| `save` | `@CachePut(books) + @CacheEvict(booksPage)` | refresh book, drop stale list |
| `deleteByIsbn` | `@CacheEvict(books + booksPage)` | drop book + list |
| `existsByIsbn` | manual cache lookup, DB fallback | fast existence check |
