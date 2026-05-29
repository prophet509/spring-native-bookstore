package com.locpham.bookstore.searchservice.config;

import com.locpham.bookstore.searchservice.adapter.out.ElasticsearchRepository;
import com.locpham.bookstore.searchservice.adapter.out.persistence.elaticsearch.ElasticsearchBookDocument;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import reactor.core.publisher.Mono;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    @Profile("default")
    ApplicationRunner seedSearchIndex(ElasticsearchRepository repository) {
        return args -> repository
                .count()
                .flatMap(
                        count -> {
                            if (count > 0) {
                                log.info("ES index already has {} documents, skipping seed", count);
                                return Mono.empty();
                            }
                            log.info("ES index empty, seeding {} sample documents", SAMPLE_BOOKS.size());
                            return repository.saveAll(SAMPLE_BOOKS)
                                    .collectList()
                                    .doOnSuccess(
                                            docs ->
                                                    log.info(
                                                            "Seeded {} books into ES index",
                                                            docs.size()));
                        })
                .onErrorResume(
                        e -> {
                            log.warn("Could not seed ES index (ES might not be running): {}",
                                    e.getMessage());
                            return Mono.empty();
                        })
                .block();
    }

    private static final List<ElasticsearchBookDocument> SAMPLE_BOOKS = List.of(
            new ElasticsearchBookDocument("9781617296956", "Cloud Native Spring in Action",
                    "Thomas Vitale", 49.90, "Manning"),
            new ElasticsearchBookDocument("9781617298295", "Spring Boot in Practice",
                    "Somnath Musib", 44.99, "Manning"),
            new ElasticsearchBookDocument("9781617293986", "Spring Microservices in Action",
                    "John Carnell", 49.99, "Manning"),
            new ElasticsearchBookDocument("9781492091700", "Reactive Spring",
                    "Josh Long", 39.99, "O'Reilly"),
            new ElasticsearchBookDocument("9781617297731", "Spring Security in Action",
                    "Laurentiu Spilca", 54.99, "Manning"),
            new ElasticsearchBookDocument("9781484275989", "Pro Spring 6",
                    "Iuliana Cosmina", 59.99, "Apress"),
            new ElasticsearchBookDocument("9781492076974", "Spring Boot Up & Running",
                    "Mark Heckler", 34.99, "O'Reilly"),
            new ElasticsearchBookDocument("9781803233307", "Learning Spring Boot 3.0",
                    "Greg L. Turnquist", 49.99, "Packt"),
            new ElasticsearchBookDocument("9781484280013", "Spring Framework 6",
                    "Joseph B. Ottinger", 44.99, "Apress"),
            new ElasticsearchBookDocument("9781617298028", "Spring Data JPA",
                    "Michael J. Simons", 39.99, "Manning"),
            new ElasticsearchBookDocument("9781492057512", "Kubernetes Best Practices",
                    "Brendan Burns", 54.99, "O'Reilly"),
            new ElasticsearchBookDocument("9781098133521", "Microservices Security in Action",
                    "Prabath Siriwardena", 49.99, "Manning"),
            new ElasticsearchBookDocument("9781492084907", "Observability Engineering",
                    "Charity Majors", 59.99, "O'Reilly"),
            new ElasticsearchBookDocument("9781835085240", "Spring Boot 4 Cookbook",
                    "Alex Antonov", 44.99, "Packt"),
            new ElasticsearchBookDocument("9781617293153", "Spring in Action",
                    "Craig Walls", 52.99, "Manning"),
            new ElasticsearchBookDocument("9781492043458", "Building Microservices",
                    "Sam Newman", 49.99, "O'Reilly"),
            new ElasticsearchBookDocument("9781617292545", "Java 8 in Action",
                    "Raoul-Gabriel Urma", 39.99, "Manning"),
            new ElasticsearchBookDocument("9781491952023", "Designing Data-Intensive Applications",
                    "Martin Kleppmann", 49.99, "O'Reilly"),
            new ElasticsearchBookDocument("9781617299600", "API Security in Action",
                    "Neil Madden", 54.99, "Manning"),
            new ElasticsearchBookDocument("9781835080207", "Java Concurrency in Practice 2E",
                    "Brian Goetz", 59.99, "Packt"),
            new ElasticsearchBookDocument("9781491950357", "Kafka The Definitive Guide",
                    "Gwen Shapira", 44.99, "O'Reilly"),
            new ElasticsearchBookDocument("9781617294136", "gRPC Up & Running",
                    "Kasun Indrasiri", 39.99, "O'Reilly"),
            new ElasticsearchBookDocument("9781492090717", "Terraform Up & Running",
                    "Yevgeniy Brikman", 54.99, "O'Reilly"),
            new ElasticsearchBookDocument("9781617298929", "Podman in Action",
                    "Daniel Walsh", 49.99, "Manning"),
            new ElasticsearchBookDocument("9781484256220", "Practical Rust Projects",
                    "Shing Lyu", 44.99, "Apress"));
}
