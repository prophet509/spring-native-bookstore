package com.locpham.bookstore.catalogservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.locpham.bookstore.catalogservice.domain.Book;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class CatalogServiceApplicationTests {

    @Autowired private WebTestClient webTestClient;

    @Test
    void whenPostRequestToBookToCreate() {
        var expectingBook = Book.build("1234567899", "Title", "Author", 9.90, "Polarsophia");
        webTestClient
                .post()
                .uri("/books")
                .bodyValue(expectingBook)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(Book.class)
                .value(
                        actualBook -> {
                            assertThat(actualBook).isNotNull();
                            assertThat(actualBook.isbn()).isEqualTo(expectingBook.isbn());
                            assertThat(actualBook.title()).isEqualTo(expectingBook.title());
                            assertThat(actualBook.author()).isEqualTo(expectingBook.author());
                            assertThat(actualBook.price()).isEqualTo(expectingBook.price());
                            assertThat(actualBook.publisher()).isEqualTo(expectingBook.publisher());
                        });
    }

    @Test
    void contextLoads() {}
}
