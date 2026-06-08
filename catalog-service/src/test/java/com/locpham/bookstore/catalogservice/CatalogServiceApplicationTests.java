package com.locpham.bookstore.catalogservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.locpham.bookstore.catalogservice.adapter.in.dto.BookRequest;
import com.locpham.bookstore.catalogservice.adapter.in.dto.BookResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(TestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class CatalogServiceApplicationTests {

    @Autowired private WebTestClient webTestClient;

    @MockitoBean private JwtDecoder jwtDecoder;

    @Test
    void whenPostRequestToBookToCreate() {
        var request = new BookRequest("1234567899", "Title", "Author", 9.90, "Polarsophia");
        given(jwtDecoder.decode("token"))
                .willReturn(
                        Jwt.withTokenValue("token")
                                .header("alg", "none")
                                .claim("sub", "employee")
                                .claim("roles", java.util.List.of("employee"))
                                .build());

        webTestClient
                .post()
                .uri("/books")
                .headers(headers -> headers.setBearerAuth("token"))
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isCreated()
                .expectBody(BookResponse.class)
                .value(
                        actualBook -> {
                            assertThat(actualBook).isNotNull();
                            assertThat(actualBook.isbn()).isEqualTo(request.isbn());
                            assertThat(actualBook.title()).isEqualTo(request.title());
                            assertThat(actualBook.author()).isEqualTo(request.author());
                            assertThat(actualBook.price()).isEqualTo(request.price());
                            assertThat(actualBook.publisher()).isEqualTo(request.publisher());
                        });
    }

    @Test
    void contextLoads() {}
}
