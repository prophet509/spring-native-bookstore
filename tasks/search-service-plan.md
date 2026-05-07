# Search Service — Implementation Plan

> **Reference:** `catalog-service` (hexagonal, Spring Data JDBC) + `order-service` (Kafka consumer pattern)
> **Port:** 9005 | **Stack:** Spring Boot 4.0.3 · Spring WebFlux · Spring Data Elasticsearch (Reactive) · Spring Cloud Stream (Kafka)

---

## Target Structure

```
search-service/
├── domain/
│   └── BookDocument.java           ← pure domain record (zero annotations)
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   └── SearchBooksUseCase.java
│   │   └── out/
│   │       └── BookIndexPort.java  ← index, delete by ISBN
│   └── service/
│       └── BookSearchService.java  ← implements SearchBooksUseCase
├── adapter/
│   ├── in/
│   │   ├── web/
│   │   │   └── SearchController.java
│   │   └── messaging/
│   │       ├── BookEventConsumer.java
│   │       └── messages/
│   │           ├── BookCreatedMessage.java
│   │           ├── BookUpdatedMessage.java
│   │           └── BookDeletedMessage.java
│   └── out/
│       └── elasticsearch/
│           ├── ElasticsearchBookDocument.java   ← @Document + @Field annotations
│           └── ElasticsearchBookRepository.java ← implements BookIndexPort, maps domain ↔ ES entity
└── bootstrap/
    ├── SearchServiceApplication.java
    └── config/
        └── ElasticsearchConfig.java
```

---

## Phase 1 — Bootstrap Project

### 1.1 build.gradle
Dependencies cần thêm:
```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'
    implementation 'org.springframework.cloud:spring-cloud-starter-config'
    implementation 'org.springframework.cloud:spring-cloud-stream'
    implementation 'org.springframework.cloud:spring-cloud-stream-binder-kafka'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.cloud:spring-cloud-stream-test-binder'
    testImplementation 'org.testcontainers:elasticsearch'
    testImplementation 'org.testcontainers:junit-jupiter'
}
```

### 1.2 application.yml (in config/)
```yaml
# config/search-service.yml
server:
  port: 9005

spring:
  elasticsearch:
    uris: http://localhost:9200
  cloud:
    stream:
      bindings:
        handleBookCreated-in-0:
          destination: book.created
          group: search-service
        handleBookUpdated-in-0:
          destination: book.updated
          group: search-service
        handleBookDeleted-in-0:
          destination: book.deleted
          group: search-service
      function:
        definition: handleBookCreated;handleBookUpdated;handleBookDeleted
```

---

## Phase 2 — Domain

### 2.1 BookDocument.java (pure domain — ZERO external dependencies)
```java
package com.locpham.bookstore.searchservice.domain;

public record BookDocument(
    String isbn,
    String title,
    String author,
    Double price,
    String publisher
) {}
```

> **Rule:** No Spring, no Jackson, no validation annotations. Just the data.

### 2.2 ElasticsearchBookDocument.java (adapter layer — Spring Data ES annotations)

```java
package com.locpham.bookstore.searchservice.adapter.out.persistence;

import com.locpham.bookstore.searchservice.domain.BookDocument;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

@Document(indexName = "books")
public record ElasticsearchBookDocument(
        @Id String isbn,
        @MultiField(
                mainField = @Field(type = FieldType.Text, analyzer = "english"),
                otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword))
        String title,
        @Field(type = FieldType.Text, analyzer = "english") String author,
        @Field(type = FieldType.Scaled_Float, scalingFactor = 100) Double price,
        @Field(type = FieldType.Keyword) String publisher
) {
  static ElasticsearchBookDocument fromDomain(BookDocument doc) {
    return new ElasticsearchBookDocument(doc.isbn(), doc.title(), doc.author(), doc.price(), doc.publisher());
  }

  BookDocument toDomain() {
    return new BookDocument(isbn, title, author, price, publisher);
  }
}
```

> **Pattern:** Same as `BookEntity` in `catalog-service`. All framework annotations stay in the adapter layer.

---

## Phase 3 — Outbound Port + Elasticsearch Adapter

### 3.1 BookIndexPort.java
```java
public interface BookIndexPort {
    Mono<BookDocument> save(BookDocument doc);
    Mono<Void> deleteByIsbn(String isbn);
    Mono<BookDocument> findByIsbn(String isbn);
}
```

### 3.2 ElasticsearchBookRepository.java
- Extend `ReactiveElasticsearchRepository<ElasticsearchBookDocument, String>`
- Implement `BookIndexPort` as a `@Repository` adapter
- Map `BookDocument` (domain) ↔ `ElasticsearchBookDocument` (adapter entity) in every method
- Spring Data ES reactive auto-implements `save`, `deleteById`, `findById` returning `Mono`

---

## Phase 4 — Application Service

### 4.1 SearchBooksUseCase.java (inbound port)
```java
public interface SearchBooksUseCase {
    Mono<Page<BookDocument>> search(String query, Pageable pageable);
    Mono<Page<BookDocument>> searchByAuthor(String author, Pageable pageable);
    Flux<String> suggest(String prefix);
}
```

### 4.2 BookSearchService.java
- Inject `ReactiveElasticsearchOperations` for rich async queries (highlight, suggest)
- Inject `BookIndexPort` for write operations
- Work with pure `BookDocument` (domain) — never touches `ElasticsearchBookDocument`
- Methods return `Mono`/`Flux`:
  - `search(String q, Pageable)` → `Mono<Page<BookDocument>>` with highlighted snippets
  - `searchByAuthor(String author, Pageable)` → `Mono<Page<BookDocument>>` keyword filter
  - `suggest(String prefix)` → `Flux<String>` from prefix query on `title.keyword`

---

## Phase 5 — Kafka Consumer (Inbound Adapter)

### 5.1 Message records
```java
// messages consumed from catalog-service topics
public record BookCreatedMessage(String isbn, String title, String author, Double price, String publisher) {}
public record BookUpdatedMessage(String isbn, String title, String author, Double price, String publisher) {}
public record BookDeletedMessage(String isbn) {}
```

### 5.2 BookEventConsumer.java
```java
@Configuration
public class BookEventConsumer {
    @Bean
    public Consumer<Flux<BookCreatedMessage>> handleBookCreated(BookIndexPort port) {
        return flux -> flux.flatMap(msg ->
            port.save(new BookDocument(msg.isbn(), msg.title(), msg.author(), msg.price(), msg.publisher()))
        ).subscribe();
    }

    @Bean
    public Consumer<Flux<BookUpdatedMessage>> handleBookUpdated(BookIndexPort port) {
        return flux -> flux.flatMap(msg ->
            port.save(new BookDocument(msg.isbn(), msg.title(), msg.author(), msg.price(), msg.publisher()))
        ).subscribe();
    }

    @Bean
    public Consumer<Flux<BookDeletedMessage>> handleBookDeleted(BookIndexPort port) {
        return flux -> flux.flatMap(msg -> port.deleteByIsbn(msg.isbn())).subscribe();
    }
}
```

---

## Phase 6 — Web Adapter

### 6.1 SearchController.java (@RestController WebFlux)
Endpoints return `Mono`/`Flux`:
```
GET /search?q=spring&page=0&size=10          → Mono<Page<SearchResponse>>
GET /search?author=vitale&sort=price,asc     → Mono<Page<SearchResponse>>
GET /search/suggest?q=spr                  → Flux<String>
```

### 6.2 SearchResponse.java DTO
```java
public record SearchResponse(
    String isbn,
    String title,        // may contain <em>highlighted</em> fragments
    String author,
    Double price,
    String publisher
) {}
```

---

## Phase 7 — catalog-service Event Publishing

**Cần thêm vào catalog-service:**

### 7.1 BookEventPublisher (outbound port)
```java
// application/port/out/
public interface BookEventPublisher {
    void publishBookCreated(Book book);
    void publishBookUpdated(Book book);
    void publishBookDeleted(String isbn);
}
```

### 7.2 KafkaBookEventPublisher (adapter/out/messaging/)
- Spring Cloud Stream `StreamBridge`
- Publish to `book.created`, `book.updated`, `book.deleted`

### 7.3 Wire into BookCatalogService
- `addBookToCatalog()` → call `publishBookCreated` after save
- `editBookDetails()` → call `publishBookUpdated` after save
- `deleteBook()` → call `publishBookDeleted` before/after delete

### 7.4 catalog-service build.gradle additions
```groovy
implementation 'org.springframework.cloud:spring-cloud-stream'
implementation 'org.springframework.cloud:spring-cloud-stream-binder-kafka'
```

### 7.5 config/catalog-service.yml additions
```yaml
spring:
  cloud:
    stream:
      bindings:
        book-created-out-0:
          destination: book.created
        book-updated-out-0:
          destination: book.updated
        book-deleted-out-0:
          destination: book.deleted
```

---

## Phase 8 — Infrastructure

### 8.1 Docker Compose additions (polar-deployment/docker/)
```yaml
# Add to compose.yml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.13.0
  container_name: polar-elasticsearch
  environment:
    - discovery.type=single-node
    - xpack.security.enabled=false
    - ES_JAVA_OPTS=-Xms512m -Xmx512m
  ports:
    - "9200:9200"
  volumes:
    - polar-elasticsearch-data:/usr/share/elasticsearch/data
```

### 8.2 Kubernetes manifests (polar-deployment/kubernetes/local/)
- `elasticsearch-deployment.yml`
- `search-service-deployment.yml`

---

## Phase 9 — Tests

### 9.1 BookSearchServiceTest (unit)
- Mock `BookIndexPort` + `ElasticsearchOperations`
- Test search delegation logic

### 9.2 BookEventConsumerTest (Spring Cloud Stream test binder)
- Verify `book.created` → ES index called
- Verify `book.deleted` → ES delete called
- Same pattern as `OrderEventConsumerTest` in inventory-service

### 9.3 SearchControllerWebTests (WebFlux slice)
- `@WebFluxTest(SearchController.class)`
- Mock `SearchBooksUseCase`
- Test `GET /search?q=spring` → 200 + body via `WebTestClient`

### 9.4 ElasticsearchRepositoryIntegrationTest
```java
@SpringBootTest
@Testcontainers
class ElasticsearchRepositoryIntegrationTest {
    @Container
    static ElasticsearchContainer es = new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.13.0");

    // test save, findByIsbn, deleteByIsbn
}
```

### 9.5 SearchServiceApplicationTests (e2e)
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@AutoConfigureWebTestClient`
- Full context with Testcontainers ES
- POST book event → `WebTestClient` search → verify indexed

---

## Implementation Order

| Phase | Task | Risk | Verify |
|-------|------|------|--------|
| 1 | Bootstrap project + build.gradle | ✅ Low | `./gradlew build` |
| 2 | BookDocument domain | ✅ Low | Unit test |
| 3 | Elasticsearch adapter | ⚠️ Medium | TC integration test |
| 4 | Application service | ✅ Low | Mocked unit test |
| 5 | Kafka consumer | ✅ Low | Stream test binder |
| 6 | SearchController | ✅ Low | MockMvc test |
| 7 | catalog-service events | ⚠️ Medium | catalog tests still green |
| 8 | Docker Compose + infra | ✅ Low | `make infra-up` |
| 9 | All tests green | — | `./gradlew test` both services |

---

## Kafka Topic Naming Convention

| Event | Topic | Producer | Consumer |
|-------|-------|----------|---------|
| Book created | `book.created` | catalog-service | search-service |
| Book updated | `book.updated` | catalog-service | search-service |
| Book deleted | `book.deleted` | catalog-service | search-service |

> **Rule:** Follow `<domain>.<event>` convention (same as `order.created`, `order.cancelled` in order-service).
