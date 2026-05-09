# Search Service — Implementation Plan (Reactive)

> **Reference:** `catalog-service` (hexagonal, Spring Data JDBC) + `order-service` (reactive Kafka consumer pattern)
> **Port:** 9005 | **Stack:** Spring Boot 4.0.6 · Spring WebFlux · Spring Data Elasticsearch (Reactive) · Spring Cloud Stream Kafka (Reactive bindings)

---

## Reactive Strategy (đọc trước khi code)

**Quyết định kiến trúc:** Toàn bộ pipeline đi theo Reactive Streams — không có blocking call trên event-loop của Netty.

| Hướng | Pattern | Ghi chú |
|-------|---------|---------|
| Consumer (Kafka → service) | `Function<Flux<T>, Mono<Void>>` bean | Spring Cloud Stream binder tự subscribe, có backpressure |
| Producer (catalog-service → Kafka) | Functional Supplier: `Sinks.Many<Message<?>>` + `Supplier<Flux<Message<?>>>` bean | Spring Cloud Stream subscribe vào Flux source; application code chỉ push vào sink |
| Fallback (chỉ khi cần dynamic destination) | `StreamBridge.send(...)` wrapped với `.subscribeOn(Schedulers.boundedElastic())` | StreamBridge.send trả `boolean` → thực chất blocking; phải đẩy sang elastic scheduler |

**Anti-patterns cấm dùng:**
- ❌ `Consumer<Flux<T>>` với `.subscribe()` bên trong — mất backpressure, mất signal lỗi cho binder.
- ❌ `Function<T, Mono<R>>` đăng ký làm Kafka consumer — Spring Cloud Stream coi đó là processor request-reply, không phải subscriber.
- ❌ Đặt `@Bean` method bên trong class adapter implements port (mix application & adapter concerns).
- ❌ `Mono.fromRunnable(streamBridge.send(...))` chạy thẳng trên event-loop thread — block Netty.
- ❌ `.block()` trong production reactive chain.

**Các reactive function signature hợp lệ với Spring Cloud Stream:**

```java
// 1) Consumer reactive (sink) — terminate stream với Mono<Void>
@Bean
public Function<Flux<String>, Mono<Void>> handleEvent() {
    return flux -> flux.doOnNext(s -> log.info("got {}", s)).then();
}

// 2) Processor reactive (input → output) — canonical example từ Spring Cloud Stream docs
@Bean
public Function<Flux<String>, Flux<String>> uppercase() {
    return flux -> flux.map(String::toUpperCase);
}

// 3) Supplier reactive (source) — emit Flux liên tục
@Bean
public Supplier<Flux<String>> emitter() {
    return () -> Flux.interval(Duration.ofSeconds(1)).map(i -> "tick-" + i);
}

// 4) Functional Supplier với Sinks.Many — push từ application code vào outbound binding
@Bean
public Supplier<Flux<Message<?>>> bookEvents() { return sink::asFlux; }
```

> Trong search-service, ta dùng signature **(1)** cho consumer (Phase 5). Signature **(4)** dùng trong catalog-service publisher (Phase 7): đây là Functional Supplier, nơi Spring Cloud Stream tự subscribe `bookEvents-out-0` và gửi message ra Kafka. Signature **(2)** `uppercase()` thường xuất hiện trong tutorial Spring Cloud Stream — minh họa processor pattern, không dùng trực tiếp ở project này nhưng đáng nhớ.

**Lưu ý binder:** Hiện tại `build.gradle` đang dùng `spring-cloud-stream-binder-kafka-streams` (cho Kafka Streams DSL — stateful stream processing). Cần đổi sang `spring-cloud-stream-binder-kafka` cho reactive messaging thông thường.

📚 **Đọc hiểu sâu:**
- [Spring Cloud Stream — Producing and Consuming Messages (Reactive section)](https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/producing-and-consuming-messages.html) — chính thức giải thích vì sao `Function<Flux<?>, Mono<Void>>` được khuyến nghị thay cho `Consumer<Flux<?>>`.
- [Spring Cloud Stream — Kafka Binder Reactive](https://docs.spring.io/spring-cloud-stream/docs/current/reference/html/spring-cloud-stream-binder-kafka.html) — `concurrency` + reactive `KafkaReceiver`.
- [Spring Cloud Stream — Bindings](https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/bindings.html) — convention naming `<functionName>-in-0` / `-out-0`.

---

## Target Structure

```
search-service/
├── domain/
│   └── BookDocument.java           ← pure domain record (zero annotations)
├── application/
│   ├── port/
│   │   ├── in/
│   │   │   └── SearchBookUseCase.java
│   │   └── out/
│   │       ├── BookIndexPort.java          ← save / delete / find
│   │       └── BookEventPublisherPort.java ← (optional, nếu search-service cần publish event)
│   └── service/
│       └── SearchBookService.java          ← implements SearchBookUseCase
├── adapter/
│   ├── in/
│   │   ├── web/
│   │   │   └── SearchController.java       ← @RestController WebFlux
│   │   └── messaging/
│   │       ├── BookEventConsumerAdapter.java  ← @Configuration với reactive Function beans
│   │       └── message/
│   │           ├── BookCreatedMessage.java
│   │           ├── BookUpdatedMessage.java
│   │           └── BookDeletedMessage.java
│   └── out/
│       └── persistence/
│           ├── ElasticsearchBookDocument.java   ← @Document + @Field
│           └── ElasticsearchBookRepositoryAdapter.java  ← implements BookIndexPort
└── bootstrap/
    ├── SearchServiceApplication.java
    └── config/
        └── ElasticsearchConfig.java
```

📚 **Đọc hiểu sâu — Hexagonal Architecture:**
- [Alistair Cockburn — Original Hexagonal Architecture paper](https://alistair.cockburn.us/hexagonal-architecture) — paper gốc 2005.
- [Updated Edition (2025) PDF](https://alistaircockburn.com/hexarch%20v1.1b%20DIFFS%2020250420-1012%20paper+epub.docx.pdf) — bản cập nhật mới nhất của Cockburn + Garrido de Paz.
- [Netflix Tech Blog — Ready for changes with Hexagonal Architecture](https://netflixtechblog.com/ready-for-changes-with-hexagonal-architecture-b315ec967749) — case study production.

---

## Phase 1 — Bootstrap Project

### 1.1 build.gradle — sửa dependencies
```groovy
dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-webflux'
    implementation 'org.springframework.boot:spring-boot-starter-data-elasticsearch'

    implementation 'org.springframework.cloud:spring-cloud-starter-config'
    implementation 'org.springframework.cloud:spring-cloud-stream'
    implementation 'org.springframework.cloud:spring-cloud-stream-binder-kafka'   // ← reactive bindings

    developmentOnly 'org.springframework.boot:spring-boot-devtools'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'io.projectreactor:reactor-test'
    testImplementation 'org.springframework.boot:spring-boot-testcontainers'
    testImplementation 'org.springframework.cloud:spring-cloud-stream-test-binder'
    testImplementation 'org.testcontainers:junit-jupiter'
    testImplementation 'org.testcontainers:kafka'
    testImplementation 'org.testcontainers:elasticsearch'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}
```

**REMOVE** các dependency sai/dư:
- `org.springframework.boot:spring-boot-starter-kafka` (không cần khi đã có Spring Cloud Stream + binder-kafka)
- `org.apache.kafka:kafka-streams`
- `org.springframework.cloud:spring-cloud-stream-binder-kafka-streams`

📚 **Phân biệt 2 binder:**
- `binder-kafka` — message-driven (Function/Consumer/Supplier), reactive bindings, dùng cho service request/response.
- `binder-kafka-streams` — Kafka Streams DSL (KStream/KTable), stateful stream processing.

### 1.2 config/search-service.yml
```yaml
server:
  port: 9005

spring:
  elasticsearch:
    uris: http://localhost:9200
  cloud:
    function:
      definition: handleBookCreated;handleBookUpdated;handleBookDeleted
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
      kafka:
        binder:
          brokers: localhost:9092
```

---

## Phase 2 — Domain

### 2.1 BookDocument (pure domain — zero annotations)
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

### 2.2 ElasticsearchBookDocument (adapter entity)
Spring Data ES annotations stay in adapter layer.
```java
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
    static ElasticsearchBookDocument fromDomain(BookDocument d) {
        return new ElasticsearchBookDocument(d.isbn(), d.title(), d.author(), d.price(), d.publisher());
    }
    BookDocument toDomain() {
        return new BookDocument(isbn, title, author, price, publisher);
    }
}
```

📚 **Đọc hiểu Elasticsearch mapping:**
- [Spring Data Elasticsearch — Mapping (annotations)](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/object-mapping.html)
- [Elasticsearch — Text analyzers](https://www.elastic.co/guide/en/elasticsearch/reference/current/analysis-analyzers.html)
- [Elasticsearch — Multi-fields](https://www.elastic.co/guide/en/elasticsearch/reference/current/multi-fields.html)

---

## Phase 3 — Outbound Port + Elasticsearch Adapter

### 3.1 BookIndexPort
```java
public interface BookIndexPort {
    Mono<BookDocument> save(BookDocument doc);
    Mono<Void> deleteByIsbn(String isbn);
    Mono<BookDocument> findByIsbn(String isbn);
}
```

### 3.2 ElasticsearchBookRepositoryAdapter
- Wrap một `ReactiveElasticsearchRepository<ElasticsearchBookDocument, String>`.
- Implement `BookIndexPort`, map domain ↔ entity trong từng method.
- Tất cả method trả `Mono`.

```java
@Repository
class ElasticsearchBookRepositoryAdapter implements BookIndexPort {
    private final SpringDataEsRepo repo;
    public Mono<BookDocument> save(BookDocument doc) {
        return repo.save(ElasticsearchBookDocument.fromDomain(doc))
                   .map(ElasticsearchBookDocument::toDomain);
    }
    public Mono<Void> deleteByIsbn(String isbn) { return repo.deleteById(isbn); }
    public Mono<BookDocument> findByIsbn(String isbn) {
        return repo.findById(isbn).map(ElasticsearchBookDocument::toDomain);
    }
}
```

📚 **Reactive Elasticsearch:**
- [Spring Data Elasticsearch — Reactive Operations](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/reactive-template.html)
- [Spring Data Elasticsearch — Reactive Repositories](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/repositories/reactive-elasticsearch-repositories.html)
- [Javadoc — `ReactiveElasticsearchOperations`](https://docs.spring.io/spring-data/elasticsearch/docs/current/api/org/springframework/data/elasticsearch/core/ReactiveElasticsearchOperations.html)

---

## Phase 4 — Application Service

### 4.1 SearchBookUseCase (sửa import sai `java.awt.print.Pageable`)
```java
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;

public interface SearchBookUseCase {
    Mono<Page<BookDocument>> search(String query, Pageable pageable);
    Mono<Page<BookDocument>> searchByAuthor(String author, Pageable pageable);
    Flux<String> suggest(String prefix);
}
```

### 4.2 SearchBookService
- `@Service`, constructor injection.
- Inject `ReactiveElasticsearchOperations` cho highlight/suggest queries.
- Inject `BookIndexPort` (chỉ nếu cần ghi từ controller; consumer sẽ inject port trực tiếp).
- Reactive end-to-end — không có `.block()`.

📚 **Highlight & Suggest queries:**
- [Spring Data Elasticsearch — Highlighting](https://docs.spring.io/spring-data/elasticsearch/reference/elasticsearch/misc.html#elasticsearch.misc.highlighting)
- [Elasticsearch — Suggesters](https://www.elastic.co/guide/en/elasticsearch/reference/current/search-suggesters.html)

---

## Phase 5 — Reactive Kafka Consumer (Inbound Adapter)

### 5.1 Message records
```java
public record BookCreatedMessage(String isbn, String title, String author, Double price, String publisher) {}
public record BookUpdatedMessage(String isbn, String title, String author, Double price, String publisher) {}
public record BookDeletedMessage(String isbn) {}
```

### 5.2 BookEventConsumerAdapter — reactive bindings
```java
@Configuration
public class BookEventConsumerAdapter {
    private static final Logger log = LoggerFactory.getLogger(BookEventConsumerAdapter.class);

    @Bean
    public Function<Flux<BookCreatedMessage>, Mono<Void>> handleBookCreated(BookIndexPort port) {
        return flux -> flux
            .doOnNext(m -> log.info("book.created: {}", m.isbn()))
            .flatMap(m -> port.save(toDomain(m)))
            .then();
    }

    @Bean
    public Function<Flux<BookUpdatedMessage>, Mono<Void>> handleBookUpdated(BookIndexPort port) {
        return flux -> flux.flatMap(m -> port.save(toDomain(m))).then();
    }

    @Bean
    public Function<Flux<BookDeletedMessage>, Mono<Void>> handleBookDeleted(BookIndexPort port) {
        return flux -> flux.flatMap(m -> port.deleteByIsbn(m.isbn())).then();
    }

    private static BookDocument toDomain(BookCreatedMessage m) {
        return new BookDocument(m.isbn(), m.title(), m.author(), m.price(), m.publisher());
    }
    private static BookDocument toDomain(BookUpdatedMessage m) {
        return new BookDocument(m.isbn(), m.title(), m.author(), m.price(), m.publisher());
    }
}
```

> **Khác biệt so với plan cũ:** signature là `Function<Flux<T>, Mono<Void>>` thay vì `Consumer<Flux<T>>` + `.subscribe()`. Binder tự subscribe → giữ được backpressure & error signal.

📚 **`flatMap` vs `concatMap`:**
- [Project Reactor — Advanced Features](https://projectreactor.io/docs/core/release/reference/coreFeatures/advancedFeatures.html) — `concatMap` giữ thứ tự (sequential), `flatMap` chạy concurrent. Kafka cùng partition cần ordering → dùng `concatMap`.
- [Reactor — Which operator do I need?](https://projectreactor.io/docs/core/release/reference/whichOperator.html)

📚 **Backpressure:**
- [Reactive Streams spec](https://www.reactive-streams.org/)
- [Reactor — Backpressure & request management](https://projectreactor.io/docs/core/release/reference/coreFeatures/reactorBackpressure.html)

---

## Phase 6 — Web Adapter

### 6.1 SearchController (`@RestController` WebFlux)
```
GET /search?q=spring&page=0&size=10        → Mono<Page<SearchResponse>>
GET /search?author=vitale&sort=price,asc   → Mono<Page<SearchResponse>>
GET /search/suggest?q=spr                  → Flux<String>
```

### 6.2 SearchResponse DTO
```java
public record SearchResponse(
    String isbn,
    String title,
    String author,
    Double price,
    String publisher,
    List<String> highlights
) {}
```

📚 **Spring WebFlux:**
- [Spring Framework — WebFlux Reference](https://docs.spring.io/spring-framework/reference/web/webflux.html)
- [Spring Boot — Reactive Web](https://docs.spring.io/spring-boot/reference/web/reactive.html)

---

## Phase 7 — catalog-service Event Publishing với Functional Supplier

### 7.1 BookEventPublisher (port — application layer, KHÔNG biết Spring Cloud Stream)
```java
public interface BookEventPublisher {
    Mono<Void> publishBookCreated(Book book);
    Mono<Void> publishBookUpdated(Book book);
    Mono<Void> publishBookDeleted(String isbn);
}
```

### 7.2 CatalogBookEventStreamConfig — Functional Supplier sources
```java
@Configuration
public class CatalogBookEventStreamConfig {

    @Bean
    public Sinks.Many<BookCreatedMessage> bookCreatedSink() {
        return Sinks.many().unicast().onBackpressureBuffer();
    }

    @Bean
    public Sinks.Many<BookUpdatedMessage> bookUpdatedSink() {
        return Sinks.many().unicast().onBackpressureBuffer();
    }

    @Bean
    public Sinks.Many<BookDeletedMessage> bookDeletedSink() {
        return Sinks.many().unicast().onBackpressureBuffer();
    }

    @Bean
    public Supplier<Flux<BookCreatedMessage>> bookCreatedEvents(Sinks.Many<BookCreatedMessage> bookCreatedSink) {
        return bookCreatedSink::asFlux;
    }

    @Bean
    public Supplier<Flux<BookUpdatedMessage>> bookUpdatedEvents(Sinks.Many<BookUpdatedMessage> bookUpdatedSink) {
        return bookUpdatedSink::asFlux;
    }

    @Bean
    public Supplier<Flux<BookDeletedMessage>> bookDeletedEvents(Sinks.Many<BookDeletedMessage> bookDeletedSink) {
        return bookDeletedSink::asFlux;
    }
}
```

### 7.3 KafkaBookEventPublisher adapter — chỉ implements outbound port
```java
@Component
public class KafkaBookEventPublisher implements BookEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaBookEventPublisher.class);
    private final Sinks.Many<BookCreatedMessage> bookCreatedSink;
    private final Sinks.Many<BookUpdatedMessage> bookUpdatedSink;
    private final Sinks.Many<BookDeletedMessage> bookDeletedSink;

    public KafkaBookEventPublisher(
            Sinks.Many<BookCreatedMessage> bookCreatedSink,
            Sinks.Many<BookUpdatedMessage> bookUpdatedSink,
            Sinks.Many<BookDeletedMessage> bookDeletedSink) {
        this.bookCreatedSink = bookCreatedSink;
        this.bookUpdatedSink = bookUpdatedSink;
        this.bookDeletedSink = bookDeletedSink;
    }

    @Override
    public Mono<Void> publishBookCreated(Book book) {
        return emit(bookCreatedSink, "book.created", new BookCreatedMessage(
            book.isbn(), book.title(), book.author(), book.price(), book.publisher()));
    }

    @Override
    public Mono<Void> publishBookUpdated(Book book) {
        return emit(bookUpdatedSink, "book.updated", new BookUpdatedMessage(
            book.isbn(), book.title(), book.author(), book.price(), book.publisher()));
    }

    @Override
    public Mono<Void> publishBookDeleted(String isbn) {
        return emit(bookDeletedSink, "book.deleted", new BookDeletedMessage(isbn));
    }

    private <T> Mono<Void> emit(Sinks.Many<T> sink, String destination, T event) {
        return Mono.fromRunnable(() -> {
            Sinks.EmitResult result = sink.tryEmitNext(event);
            if (result.isFailure()) {
                log.error("Failed to emit event to {}: {}", destination, result);
                throw new IllegalStateException("emit failed: " + result);
            }
        });
    }
}
```

```java
public record BookCreatedMessage(String isbn, String title, String author, Double price, String publisher) {}
public record BookUpdatedMessage(String isbn, String title, String author, Double price, String publisher) {}
public record BookDeletedMessage(String isbn) {}
```

> **Ghi chú:** `CatalogBookEventStreamConfig` sở hữu Spring Cloud Stream beans. `KafkaBookEventPublisher` chỉ là outbound adapter implements port và không khai báo `@Bean`. Cách này giữ separation of concerns tốt hơn: configuration ở config layer, adapter chỉ translate domain action → event message.
>
> Ba Supplier source tạo ba outbound bindings: `bookCreatedEvents-out-0`, `bookUpdatedEvents-out-0`, `bookDeletedEvents-out-0`. Chúng publish đúng ba Kafka topics mà search-service đang consume: `book.created`, `book.updated`, `book.deleted`.
>
> `Mono.fromRunnable` ở đây chỉ chứa `tryEmitNext` (in-memory, non-blocking). Nó **khác** với `Mono.fromRunnable(streamBridge.send(...))` — cái sau có thể gọi I/O/blocking path.

📚 **Reactor Sinks deep dive — phải đọc:**
- [Project Reactor — Sinks reference](https://projectreactor.io/docs/core/release/reference/coreFeatures/sinks.html)
- [Javadoc — `Sinks.Many`](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Sinks.Many.html)
- [Javadoc — `Sinks.MulticastSpec`](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Sinks.MulticastSpec.html)

📚 **Spring Cloud Stream — Supplier source:**
- [Spring Cloud Stream — Suppliers & Sources](https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/producing-and-consuming-messages.html)
- [Spring Cloud Stream — Bindings](https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/bindings.html)

### 7.4 catalog-service config additions
```yaml
spring:
  cloud:
    function:
      definition: bookCreatedEvents;bookUpdatedEvents;bookDeletedEvents
    stream:
      bindings:
        bookCreatedEvents-out-0:
          destination: book.created
          content-type: application/json
        bookUpdatedEvents-out-0:
          destination: book.updated
          content-type: application/json
        bookDeletedEvents-out-0:
          destination: book.deleted
          content-type: application/json
      kafka:
        binder:
          brokers: ${SPRING_KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
```

### 7.5 catalog-service build.gradle additions
```groovy
implementation 'org.springframework.cloud:spring-cloud-stream'
implementation 'org.springframework.cloud:spring-cloud-stream-binder-kafka'
```

### 7.6 Wire vào BookCatalogService
```java
public Book addBookToCatalog(Book book) {
    Book saved = bookRepository.save(book);
    publisher.publishBookCreated(saved).block();
    return saved;
}

public Book editBookDetails(Book book) {
    Book saved = bookRepository.update(book);
    publisher.publishBookUpdated(saved).block();
    return saved;
}

public void removeBookFromCatalog(String isbn) {
    bookRepository.deleteByIsbn(isbn);
    publisher.publishBookDeleted(isbn).block();
}
```

> **Cảnh báo:** `catalog-service` hiện đang Spring MVC (blocking) + Spring Data JDBC, nên application service có thể block ở boundary bằng `.block()` để surface lỗi publish thay vì fire-and-forget. Không dùng `.subscribe()` trong service vì sẽ nuốt lỗi và làm flow khó kiểm soát.
>
> Nếu muốn đảm bảo atomicity giữa database write và event publish, dùng Outbox pattern ở bước production-hardening. Functional Supplier chỉ giải quyết cách publish qua Spring Cloud Stream, không tự đảm bảo exactly-once với database transaction.

📚 **Outbox pattern (đáng đọc cho production):**
- [Debezium blog — Reliable Microservices Data Exchange With the Outbox Pattern](https://debezium.io/blog/2019/02/19/reliable-microservices-data-exchange-with-the-outbox-pattern/)
- [Decodable — Revisiting the Outbox Pattern](https://www.decodable.co/blog/revisiting-the-outbox-pattern)
- [Thorben Janssen — Outbox Pattern with CDC and Debezium](https://thorben-janssen.com/outbox-pattern-with-cdc-and-debezium/)

---

## Phase 8 — Infrastructure

### 8.1 Docker Compose additions
```yaml
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
- Update `skaffold.yml` để build/deploy `search-service`.

📚 **Elasticsearch ops:**
- [Elastic — Install Elasticsearch with Docker](https://www.elastic.co/guide/en/elasticsearch/reference/current/docker.html)
- [ECK (Elasticsearch on Kubernetes) operator](https://www.elastic.co/guide/en/cloud-on-k8s/current/index.html)

---

## Phase 9 — Tests

### 9.1 SearchBookServiceTest (unit, reactive)
- Mockito + `StepVerifier`.
- Mock `BookIndexPort` + `ReactiveElasticsearchOperations`.

### 9.2 BookEventConsumerAdapterTest (Spring Cloud Stream test binder)
```java
@SpringBootTest
@Import(TestChannelBinderConfiguration.class)
class BookEventConsumerAdapterTest {
    @Autowired InputDestination input;
    @MockBean BookIndexPort port;

    @Test
    void handleBookCreated_indexesDocument() {
        when(port.save(any())).thenReturn(Mono.just(new BookDocument("isbn", "t", "a", 1.0, "p")));
        input.send(new GenericMessage<>(new BookCreatedMessage("isbn", "t", "a", 1.0, "p")),
                   "handleBookCreated-in-0");
        verify(port, timeout(1000)).save(any());
    }
}
```

### 9.3 KafkaBookEventPublisherTest (catalog-service)
- Dùng `OutputDestination` của test binder.
- Gọi `publisher.publishBookCreated(book).block()` (chỉ trong test).

### 9.4 SearchControllerWebFluxTest
- `@WebFluxTest(SearchController.class)` + `WebTestClient`.
- Mock `SearchBookUseCase`.

### 9.5 ElasticsearchRepositoryIT (Testcontainers)
```java
@SpringBootTest
@Testcontainers
class ElasticsearchRepositoryIT {
    @Container
    static ElasticsearchContainer es =
        new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:8.13.0");

    @Test
    void saveAndFind() {
        StepVerifier.create(repo.save(doc).then(repo.findByIsbn(doc.isbn())))
            .expectNextMatches(d -> d.title().equals(doc.title()))
            .verifyComplete();
    }
}
```

### 9.6 SearchServiceApplicationTests (e2e)
- Testcontainers Kafka + Elasticsearch.
- Publish event → poll search endpoint qua `WebTestClient` → verify indexed.

📚 **Testing reactive + messaging:**
- [Spring Cloud Stream — Testing](https://docs.spring.io/spring-cloud-stream/reference/spring-cloud-stream/testing.html)
- [Reactor Test — `StepVerifier`](https://projectreactor.io/docs/core/release/reference/testing.html)
- [Testcontainers — Elasticsearch](https://java.testcontainers.org/modules/elasticsearch/)
- [Testcontainers — Kafka](https://java.testcontainers.org/modules/kafka/)
- [BlockHound](https://github.com/reactor/BlockHound) — detect blocking call trong test.

---

## Implementation Order

| Phase | Task | Risk | Verify |
|-------|------|------|--------|
| 1 | Sửa `search-service/build.gradle` (drop kafka-streams binder, add kafka binder) | ✅ Low | `./gradlew build` |
| 2 | Domain `BookDocument` | ✅ Low | compile |
| 3 | ES adapter + `BookIndexPort` | ⚠️ Medium | TC integration test |
| 4 | Application service (fix `Pageable` import) | ✅ Low | unit test |
| 5 | Reactive consumer bindings | ⚠️ Medium | Stream test binder |
| 6 | WebFlux `SearchController` | ✅ Low | `WebTestClient` |
| 7 | catalog-service publisher | 🔴 High | catalog tests + e2e |
| 8 | Docker Compose + k8s ES | ✅ Low | `make infra-up` |
| 9 | All tests green | — | `./gradlew test` cả 2 service |

---

## Kafka Topic Naming Convention

| Event | Topic | Producer | Consumer |
|-------|-------|----------|----------|
| Book created | `book.created` | catalog-service | search-service |
| Book updated | `book.updated` | catalog-service | search-service |
| Book deleted | `book.deleted` | catalog-service | search-service |

> **Rule:** `<domain>.<event>` (giống `order.created`, `order.cancelled`).

---

## Reactive Sanity Checklist trước khi merge

- [ ] Không có `.block()` nào trong `src/main`
- [ ] Không có `Consumer<Flux<T>>` + `.subscribe()` trong consumer adapter
- [ ] Không import `java.awt.print.Pageable` (sai package)
- [ ] `StreamBridge.send` (nếu dùng) chỉ chạy trên blocking stack hoặc `.subscribeOn(Schedulers.boundedElastic())`
- [ ] `BlockHound` (optional) bật trong test để detect blocking call trên event-loop
- [ ] Port interface không leak Spring Cloud Stream / Kafka type (`Function`, `Message`, `StreamBridge`)

---

## Bonus — Nếu muốn bỏ Spring Cloud Stream

Khi cần fine-grained control hoặc loại bỏ abstraction, dùng `reactor-kafka` trực tiếp:
- [Reactor Kafka reference guide](https://projectreactor.io/docs/kafka/release/reference/)
- [Spring for Apache Kafka — Reactive Kafka](https://docs.spring.io/spring-kafka/reference/) — `ReactiveKafkaProducerTemplate`, `ReactiveKafkaConsumerTemplate`.
