package com.locpham.bookstore.catalogservice.config;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Extracts Spring Security {@link GrantedAuthority} entries from a Keycloak access token.
 *
 * <p>Keycloak places realm-level roles inside {@code realm_access.roles} and client-level roles
 * inside {@code resource_access.<client-id>.roles}. This converter merges both into a single
 * collection and applies the {@code ROLE_} prefix expected by {@code hasRole(...)} expressions.
 */
public final class KeycloakJwtAuthoritiesConverter
        implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String DIRECT_ROLES_CLAIM = "roles";
    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String AUTHORITY_PREFIX = "ROLE_";

    private final String clientId;

    public KeycloakJwtAuthoritiesConverter(String clientId) {
        this.clientId = clientId;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return Stream.concat(
                        directRoles(jwt).stream(),
                        Stream.concat(realmRoles(jwt).stream(), clientRoles(jwt).stream()))
                .distinct()
                .map(role -> new SimpleGrantedAuthority(AUTHORITY_PREFIX + role))
                .collect(Collectors.toUnmodifiableList());
    }

    private Collection<String> directRoles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(DIRECT_ROLES_CLAIM);
        return roles == null ? Collections.emptyList() : roles;
    }

    private Collection<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        return rolesFrom(realmAccess);
    }

    @SuppressWarnings("unchecked")
    private Collection<String> clientRoles(Jwt jwt) {
        if (clientId == null || clientId.isBlank()) {
            return Collections.emptyList();
        }
        Map<String, Object> resourceAccess = jwt.getClaimAsMap(RESOURCE_ACCESS_CLAIM);
        if (resourceAccess == null) {
            return Collections.emptyList();
        }
        Object client = resourceAccess.get(clientId);
        if (!(client instanceof Map<?, ?> clientMap)) {
            return Collections.emptyList();
        }
        return rolesFrom((Map<String, Object>) clientMap);
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> rolesFrom(Map<String, Object> claim) {
        if (claim == null) {
            return Collections.emptyList();
        }
        Object roles = claim.get(ROLES_CLAIM);
        if (roles instanceof Collection<?> collection) {
            return (List<String>) collection.stream().map(Object::toString).toList();
        }
        return Collections.emptyList();
    }
}
