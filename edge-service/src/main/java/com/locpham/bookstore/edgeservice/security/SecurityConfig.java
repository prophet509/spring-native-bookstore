package com.locpham.bookstore.edgeservice.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.server.WebSessionServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

/**
 * Hybrid edge security:
 *
 * <ul>
 *   <li><b>actuatorSecurityFilterChain</b> ({@link Ordered#HIGHEST_PRECEDENCE}) —
 *       health/Prometheus.
 *   <li><b>apiBearerSecurityFilterChain</b> (HIGHEST_PRECEDENCE+10) — matches requests carrying an
 *       {@code Authorization: Bearer ...} header. Validates JWT against Keycloak (resource server),
 *       maps the {@code roles} claim to {@code ROLE_*} authorities, and disables CSRF since these
 *       requests are stateless. Used by API/SDK/load-test clients.
 *   <li><b>springSecurityFilterChain</b> ({@link Ordered#LOWEST_PRECEDENCE}) — browser flow with
 *       {@code oauth2Login}, session cookies, and CSRF protection. Used by the SPA / browser users.
 * </ul>
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /** Matches exchanges where the {@code Authorization} header starts with {@code Bearer }. */
    private static final ServerWebExchangeMatcher BEARER_TOKEN_MATCHER =
            exchange -> {
                String header = exchange.getRequest().getHeaders().getFirst("Authorization");
                return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)
                        ? ServerWebExchangeMatcher.MatchResult.match()
                        : ServerWebExchangeMatcher.MatchResult.notMatch();
            };

    @Bean
    ServerOAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new WebSessionServerOAuth2AuthorizedClientRepository();
    }

    @Bean
    GrantedAuthoritiesMapper authoritiesMapper() {
        return authorities -> {
            Set<GrantedAuthority> mappedAuthorities = new LinkedHashSet<>();

            for (GrantedAuthority authority : authorities) {
                if (authority instanceof OidcUserAuthority oidcAuthority) {
                    List<String> roles = oidcAuthority.getUserInfo().getClaimAsStringList("roles");
                    if (roles == null || roles.isEmpty()) {
                        roles = oidcAuthority.getIdToken().getClaimAsStringList("roles");
                    }
                    if (roles != null) {
                        mappedAuthorities.addAll(
                                roles.stream()
                                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                        .collect(Collectors.toSet()));
                    }
                } else {
                    mappedAuthorities.add(authority);
                }
            }

            return mappedAuthorities.isEmpty() ? Collections.emptySet() : mappedAuthorities;
        };
    }

    /**
     * Maps the custom {@code roles} claim from the realm-roles protocol mapper to {@code
     * ROLE_<name>} authorities so {@code .hasRole("customer")} works the same way as in the browser
     * session flow.
     */
    @Bean
    ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
        delegate.setJwtGrantedAuthoritiesConverter(
                jwt -> {
                    Collection<GrantedAuthority> fromClaim = authoritiesConverter.convert(jwt);
                    return fromClaim != null ? fromClaim : Collections.emptyList();
                });
        // principal name comes from preferred_username when present so rate-limiter buckets by user
        delegate.setPrincipalClaimName("preferred_username");

        return new ReactiveJwtAuthenticationConverterAdapter(
                (org.springframework.core.convert.converter.Converter<
                                Jwt, AbstractAuthenticationToken>)
                        delegate);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    SecurityWebFilterChain actuatorSecurityFilterChain(ServerHttpSecurity http) {
        return http.securityMatcher(
                        ServerWebExchangeMatchers.pathMatchers(
                                "/actuator/prometheus", "/actuator/health/**"))
                .authorizeExchange(
                        exchange ->
                                exchange.pathMatchers("/actuator/health/**")
                                        .permitAll()
                                        .pathMatchers("/actuator/prometheus")
                                        .hasRole("ADMIN"))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }

    /**
     * Stateless filter chain for API/SDK/load-test clients sending {@code Authorization: Bearer
     * <jwt>}. Runs <em>before</em> the browser-session chain so Bearer requests never go through
     * {@code oauth2Login} (which would issue a 302 to Keycloak).
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    SecurityWebFilterChain apiBearerSecurityFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter) {
        return http.securityMatcher(BEARER_TOKEN_MATCHER)
                .authorizeExchange(
                        exchange ->
                                exchange.pathMatchers(HttpMethod.GET, "/books/**")
                                        .permitAll()
                                        .pathMatchers(HttpMethod.GET, "/search/**")
                                        .permitAll()
                                        .pathMatchers(HttpMethod.POST, "/books/**")
                                        .hasRole("employee")
                                        .pathMatchers(HttpMethod.PUT, "/books/**")
                                        .hasRole("employee")
                                        .pathMatchers(HttpMethod.DELETE, "/books/**")
                                        .hasRole("employee")
                                        .pathMatchers(HttpMethod.POST, "/orders/**")
                                        .hasAnyRole("customer", "employee")
                                        .pathMatchers(HttpMethod.GET, "/orders/**")
                                        .authenticated()
                                        .anyExchange()
                                        .authenticated())
                .oauth2ResourceServer(
                        rs ->
                                rs.jwt(
                                        jwtSpec ->
                                                jwtSpec.jwtAuthenticationConverter(
                                                        jwtAuthenticationConverter)))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .headers(
                        headers ->
                                headers.contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'"))
                                        .referrerPolicy(
                                                ref ->
                                                        ref.policy(
                                                                ReferrerPolicyServerHttpHeadersWriter
                                                                        .ReferrerPolicy
                                                                        .STRICT_ORIGIN))
                                        .permissionsPolicy(
                                                perm ->
                                                        perm.policy(
                                                                "camera=(), microphone=(), geolocation=()"))
                                        .hsts(Customizer.withDefaults()))
                .exceptionHandling(
                        eh ->
                                eh.authenticationEntryPoint(
                                        new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                .build();
    }

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            ReactiveClientRegistrationRepository clientRegistrationRepository) {
        return http.authorizeExchange(
                        exchange ->
                                exchange.pathMatchers("/actuator/**")
                                        .hasRole("ADMIN")
                                        .pathMatchers("/", "/*.css", "/*.js", "/favicon.ico")
                                        .permitAll()
                                        .pathMatchers(HttpMethod.GET, "/books/**")
                                        .permitAll()
                                        .pathMatchers(HttpMethod.GET, "/search/**")
                                        .permitAll()
                                        .pathMatchers(HttpMethod.POST, "/books/**")
                                        .hasRole("employee")
                                        .pathMatchers(HttpMethod.PUT, "/books/**")
                                        .hasRole("employee")
                                        .pathMatchers(HttpMethod.DELETE, "/books/**")
                                        .hasRole("employee")
                                        .pathMatchers(HttpMethod.POST, "/orders/**")
                                        .hasAnyRole("customer", "employee")
                                        .pathMatchers(HttpMethod.GET, "/orders/**")
                                        .authenticated()
                                        .anyExchange()
                                        .authenticated())
                .exceptionHandling(
                        exceptionHandling ->
                                exceptionHandling.authenticationEntryPoint(
                                        new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                .oauth2Login(Customizer.withDefaults())
                .logout(
                        logout ->
                                logout.logoutSuccessHandler(
                                        oidcLogoutSuccessHandler(clientRegistrationRepository)))
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(
                                        CookieServerCsrfTokenRepository.withHttpOnlyFalse()))
                .headers(
                        headers ->
                                headers.contentSecurityPolicy(
                                                csp ->
                                                        csp.policyDirectives(
                                                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'"))
                                        .referrerPolicy(
                                                ref ->
                                                        ref.policy(
                                                                ReferrerPolicyServerHttpHeadersWriter
                                                                        .ReferrerPolicy
                                                                        .STRICT_ORIGIN))
                                        .permissionsPolicy(
                                                perm ->
                                                        perm.policy(
                                                                "camera=(), microphone=(), geolocation=()"))
                                        .hsts(Customizer.withDefaults()))
                .build();
    }

    @Bean
    WebFilter csrfWebFilter() {
        return (exchange, chain) -> {
            exchange.getResponse()
                    .beforeCommit(
                            () ->
                                    Mono.defer(
                                            () -> {
                                                Mono<CsrfToken> csrfToken =
                                                        exchange.getAttribute(
                                                                CsrfToken.class.getName());
                                                return csrfToken != null
                                                        ? csrfToken.then()
                                                        : Mono.empty();
                                            }));
            return chain.filter(exchange);
        };
    }

    private ServerLogoutSuccessHandler oidcLogoutSuccessHandler(
            ReactiveClientRegistrationRepository clientRegistrationRepository) {
        var oidcLogoutSuccessHandler =
                new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
        oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}");
        return oidcLogoutSuccessHandler;
    }
}
