package com.locpham.bookstore.catalogservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakJwtAuthoritiesConverterTests {

    private final KeycloakJwtAuthoritiesConverter converter =
            new KeycloakJwtAuthoritiesConverter("edge-service");

    @Test
    void shouldExtractRealmRoles() {
        var jwt = jwtBuilder().claim("realm_access", Map.of("roles", List.of("employee"))).build();

        var authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_employee");
    }

    @Test
    void shouldExtractClientRolesForConfiguredClient() {
        var jwt =
                jwtBuilder()
                        .claim(
                                "resource_access",
                                Map.of("edge-service", Map.of("roles", List.of("admin"))))
                        .build();

        var authorities = converter.convert(jwt);

        assertThat(authorities).extracting(GrantedAuthority::getAuthority).contains("ROLE_admin");
    }

    @Test
    void shouldMergeRealmAndClientRolesWithoutDuplicates() {
        var jwt =
                jwtBuilder()
                        .claim("realm_access", Map.of("roles", List.of("employee", "shared")))
                        .claim(
                                "resource_access",
                                Map.of("edge-service", Map.of("roles", List.of("admin", "shared"))))
                        .build();

        var authorities = converter.convert(jwt);

        assertThat(authorities)
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_employee", "ROLE_admin", "ROLE_shared");
    }

    @Test
    void shouldReturnEmptyWhenNoRoleClaimsPresent() {
        var jwt = jwtBuilder().build();

        var authorities = converter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    @Test
    void shouldIgnoreClientRolesWhenClientIdNotConfigured() {
        var noClientConverter = new KeycloakJwtAuthoritiesConverter(null);
        var jwt =
                jwtBuilder()
                        .claim(
                                "resource_access",
                                Map.of("edge-service", Map.of("roles", List.of("admin"))))
                        .build();

        var authorities = noClientConverter.convert(jwt);

        assertThat(authorities).isEmpty();
    }

    private Jwt.Builder jwtBuilder() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .subject("user");
    }
}
