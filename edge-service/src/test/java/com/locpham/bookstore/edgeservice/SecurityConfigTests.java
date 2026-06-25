package com.locpham.bookstore.edgeservice;

import static org.mockito.Mockito.when;

import com.locpham.bookstore.edgeservice.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest
@Import(SecurityConfig.class)
class SecurityConfigTests {

    @Autowired private WebTestClient webTestClient;

    @MockitoBean private ReactiveClientRegistrationRepository reactiveClientRegistrationRepository;

    @Test
    void whenLogoutNotAuthenticatedAndNoCsrfTokenThen403() {
        webTestClient.post().uri("/logout").exchange().expectStatus().isForbidden();
    }

    @Test
    void whenLogoutAuthenticatedAndNoCsrfTokenThen403() {
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockOidcLogin())
                .post()
                .uri("/logout")
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void whenLogoutAuthenticatedAndWithCsrfTokenThen3xx() {
        when(reactiveClientRegistrationRepository.findByRegistrationId("test"))
                .thenReturn(Mono.just(testClientRegistration()));

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockOidcLogin())
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/logout")
                .exchange()
                .expectStatus()
                .is3xxRedirection();
    }

    @Test
    void whenBrowserRequestThenSecurityHeadersAreWritten() {
        webTestClient
                .get()
                .uri("/books")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .valueEquals(
                        "Content-Security-Policy",
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'")
                .expectHeader()
                .valueEquals("Referrer-Policy", "strict-origin")
                .expectHeader()
                .valueEquals("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    }

    @Test
    void whenSecureBrowserRequestThenHstsHeaderIsWritten() {
        webTestClient
                .get()
                .uri("https://localhost/books")
                .exchange()
                .expectStatus()
                .isNotFound()
                .expectHeader()
                .valueMatches("Strict-Transport-Security", ".*max-age=.*");
    }

    private ClientRegistration testClientRegistration() {
        return ClientRegistration.withRegistrationId("test")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientId("test")
                .authorizationUri("https://sso.example.com/auth")
                .tokenUri("https://sso.example.com/token")
                .redirectUri("https://bookstore.example.com")
                .build();
    }
}
