package com.locpham.bookstore.dispatcherservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtAuthoritiesConverterTest {

    private final KeycloakJwtAuthoritiesConverter converter = new KeycloakJwtAuthoritiesConverter();

    @Test
    void extractsDirectRoles() {
        var jwt = jwtBuilder().claim("roles", List.of("employee")).build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_employee");
    }

    @Test
    void extractsRealmRoles() {
        var jwt = jwtBuilder().claim("realm_access", Map.of("roles", List.of("admin"))).build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_admin");
    }

    @Test
    void mergesAndDeduplicates() {
        var jwt =
                jwtBuilder()
                        .claim("roles", List.of("shared"))
                        .claim("realm_access", Map.of("roles", List.of("admin", "shared")))
                        .build();

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_shared", "ROLE_admin");
    }

    @Test
    void emptyWhenNoRoleClaims() {
        assertThat(converter.convert(jwtBuilder().build())).isEmpty();
    }

    @Test
    void emptyWhenRealmAccessHasNoRolesCollection() {
        var jwt = jwtBuilder().claim("realm_access", Map.of("roles", "not-a-list")).build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    private Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("user");
    }
}
