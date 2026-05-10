# Giai Đoạn 3 — Security Hoàn Chỉnh (Vitale Ch.11–14 style)

> **Mục tiêu:** Biến `edge-service` thành một BFF (Backend For Frontend) pattern hoàn chỉnh với OAuth2 + OIDC, đồng thời bảo vệ downstream services bằng JWT validation và role-based access control.
> **Triết lý:** Build trên những gì đã có — không viết lại từ đầu. Mỗi task có file cụ thể, diff rõ ràng, và command để verify.

---

## 📊 Hiện Trạng Security (Baseline)

| Service | Đã có | Thiếu |
|---------|-------|-------|
| **edge-service** | OAuth2 Client (Auth Code + OIDC), Redis Session, TokenRelay, CSRF cookie, OIDC Logout, Circuit Breaker, Rate Limiter (IP-based) | RBAC ở gateway level, Security Headers (CSP, HSTS, v.v.), PKCE verify, per-user rate limit, `X-User-Id` propagation |
| **catalog-service** | `spring-boot-starter-oauth2-resource-server`, `JwtAuthenticationConverter` + `KeycloakJwtAuthoritiesConverter`, issuer-uri config, role check cơ bản (`hasRole("employee")`) | Method-level security (`@PreAuthorize`), Keycloak Testcontainers integration tests |
| **order-service** | `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-security`, issuer-uri config, basic `authenticated()` filter | **Custom `KeycloakJwtAuthoritiesConverter`**, role-based path matchers, method-level security, `X-User-Id` header → `Order.createdBy` |
| **inventory-service** | Không có gì | Toàn bộ: `oauth2-resource-server`, `SecurityConfig`, `KeycloakJwtAuthoritiesConverter`, issuer-uri config |
| **search-service** | Không có gì | Toàn bộ: `oauth2-resource-server`, `SecurityConfig`, `KeycloakJwtAuthoritiesConverter`, issuer-uri config |
| **dispatcher-service** | Không có gì | Không cần OAuth2 Resource Server vì service chỉ consume Kafka (không expose HTTP API). Không cần sửa trong Phase này. |

> **Lưu ý:** `search-service` đang dùng Spring Boot `4.0.6`, khác với `4.0.3` của các service khác. Cần đảm bảo dependency versions tương thích khi thêm `spring-boot-starter-oauth2-resource-server`.

---

## 🗺️ Execution Plan — 5 Phases

```
Phase 1: OAuth2 Theory & Keycloak Setup (không code, chỉ config)
Phase 2: edge-service — BFF Pattern Hoàn Chỉnh
Phase 3: Downstream JWT Validation + RBAC
Phase 4: Security Testing (Unit + Integration + Keycloak Testcontainers)
Phase 5: Rate Limiting & Observability
```

---

## Phase 1 — OAuth2 Theory & Keycloak Setup (Không code)

> **Goal:** Hiểu rõ flow trước khi sửa code. Thời gian: 1–2 giờ đọc + setup.

### 1.1 Phân biệt 3 loại token

| Token | Lifetime | Dùng cho |
|-------|----------|----------|
| **Access Token** (JWT) | 5 phút | Gọi API downstream — stateless |
| **Refresh Token** (opaque) | 30 phút | Lấy Access Token mới khi hết hạn — stateful ở Keycloak |
| **ID Token** (JWT, OIDC) | 5 phút | Thông tin user cho edge-service, **không gửi downstream** |

**Verify:**
```bash
# Đăng nhập qua edge-service → inspect Redis session
redis-cli -n 0
keys '*polar:edge*'
```

### 1.2 BFF Pattern — Tại sao token không lộ ra browser

```
Browser ←→ edge-service (giữ tokens trong server-side Redis Session)
                  ↕ (chỉ trả về session cookie cho browser, KHÔNG trả token về browser)
           downstream services (nhận Access Token qua TokenRelay)
```

### 1.3 Keycloak Realm Setup

**Mở Keycloak Admin Console** (`http://localhost:8080/admin`):

| Bước | Action | Verify |
|------|--------|--------|
| 1.3.1 | Tạo Realm `PolarBookshop` | Realm list hiển thị `PolarBookshop` |
| 1.3.2 | Tạo Client `edge-service`: <br>- Client authentication: OFF (public client) <br>- Standard flow: enabled <br>- Direct access grants: disabled <br>- Valid redirect URIs: `http://localhost:9000/login/oauth2/code/keycloak` <br>- Web origins: `http://localhost:9000` <br>- Post logout redirect URIs: `http://localhost:9000` | Client config JSON exportable |
| 1.3.3 | Bật **PKCE** trong client settings: `Proof Key for Code Exchange Code Challenge Method: S256` | Default cho public clients trong Keycloak 24+ |
| 1.3.4 | Bật **Refresh Token Rotation**: Client → Advanced → Refresh Token Rotation: ON | Mỗi refresh trả token mới |
| 1.3.5 | Tạo Realm Roles: `customer`, `employee` | Roles list có 2 roles |
| 1.3.6 | Tạo Users: <br>- `bjorn`/`bjorn` với role `employee` <br>- `isabelle`/`isabelle` với role `customer` | Login test qua edge-service OK |
| 1.3.7 | Thêm **Mapper** để đưa `roles` vào JWT claim: <br>- Client scopes → `edge-service-dedicated` → Add mapper → User Realm Role <br>- Token claim name: `roles` <br>- Add to ID token: ON, Access token: ON | Decode JWT ở jwt.io thấy claim `"roles": ["customer"]` |

---

## Phase 2 — edge-service: BFF Pattern Hoàn Chỉnh

### 2.1 PKCE Verification (Không sửa code — chỉ verify)

Spring Security 6+ tự bật PKCE cho public clients. Verify bằng browser DevTools:
- Network tab → tìm redirect đến Keycloak → query params có `code_challenge` và `code_challenge_method=S256`

**Nếu không thấy:**
- Kiểm tra client registration trong `config/edge-service.yml` không có `client-secret` (public client)
- Hoặc thêm explicit trong `@Bean` nếu cần (thường không cần)

### 2.2 Add RBAC + Security Headers vào `SecurityConfig.java`

**File:** `/Users/locpham/Desktop/Workspace/spring-native-bookstore/edge-service/src/main/java/com/locpham/bookstore/edgeservice/security/SecurityConfig.java`

**Thay thế toàn bộ file:**

```java
package com.locpham.bookstore.edgeservice.security;

import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    ServerOAuth2AuthorizedClientRepository authorizedClientRepository() {
        return new WebSessionServerOAuth2AuthorizedClientRepository();
    }

    @Bean
    GrantedAuthoritiesMapper authoritiesMapper() {
        return authorities -> {
            Set<GrantedAuthority> mappedAuthorities = authorities.stream()
                    .filter(a -> a instanceof OidcUserAuthority)
                    .map(a -> (OidcUserAuthority) a)
                    .flatMap(a -> {
                        List<String> roles = a.getUserInfo().getClaimAsStringList("roles");
                        if (roles == null) {
                            return java.util.stream.Stream.empty();
                        }
                        return roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role));
                    })
                    .collect(Collectors.toSet());

            // Giữ lại các authority không phải OidcUserAuthority
            mappedAuthorities.addAll(
                    authorities.stream()
                            .filter(a -> !(a instanceof OidcUserAuthority))
                            .collect(Collectors.toSet()));
            return mappedAuthorities;
        };
    }

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(
            ServerHttpSecurity http,
            ReactiveClientRegistrationRepository clientRegistrationRepository) {
        return http.authorizeExchange(
                        exchange ->
                                exchange.pathMatchers("/actuator/**")
                                        .permitAll()
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
                .oauth2Login(oauth2 -> oauth2.authoritiesMapper(authoritiesMapper()))
                .logout(
                        logout ->
                                logout.logoutSuccessHandler(
                                        oidcLogoutSuccessHandler(clientRegistrationRepository)))
                .csrf(
                        csrf ->
                                csrf.csrfTokenRepository(
                                        CookieServerCsrfTokenRepository.withHttpOnlyFalse()))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'"))
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN))
                        .permissionsPolicy(perm -> perm.policy("camera=(), microphone=(), geolocation=()")))
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
```

**Verify:**
```bash
cd edge-service && ./gradlew spotlessApply
```

### 2.3 Add User Context Propagation Filter

**File mới:** `/Users/locpham/Desktop/Workspace/spring-native-bookstore/edge-service/src/main/java/com/locpham/bookstore/edgeservice/security/UserIdHeaderFilter.java`

```java
package com.locpham.bookstore.edgeservice.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class UserIdHeaderFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(securityContext -> {
                    if (securityContext.getAuthentication() instanceof OAuth2AuthenticationToken token) {
                        String userId = token.getPrincipal().getName();
                        ServerWebExchange mutatedExchange = exchange.mutate()
                                .request(r -> r.header(USER_ID_HEADER, userId))
                                .build();
                        return chain.filter(mutatedExchange);
                    }
                    return chain.filter(exchange);
                })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        // Chạy sau TokenRelay (order -1) nhưng trước route filters
        return 0;
    }
}
```

**Verify:**
```bash
cd edge-service && ./gradlew bootRun
# Login qua browser → gọi GET /user → verify JSON response có roles
# Gọi GET /books (qua edge) → downstream catalog-service log có header X-User-Id
```

### 2.4 Per-User Rate Limiting

**File:** `/Users/locpham/Desktop/Workspace/spring-native-bookstore/edge-service/src/main/java/com/locpham/bookstore/edgeservice/security/RateLimiterConfig.java`

Nếu file này đã tồn tại ở `security/` hoặc root package, sửa. Nếu chưa có, tạo mới.

```java
package com.locpham.bookstore.edgeservice.security;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    @Bean
    KeyResolver userKeyResolver() {
        return exchange -> ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getName())
                .defaultIfEmpty("anonymous");
    }
}
```

**File config update:** `/Users/locpham/Desktop/Workspace/spring-native-bookstore/config/edge-service.yml`

Thêm `key-resolver` vào `RequestRateLimiter`:
```yaml
- name: RequestRateLimiter
  args:
    redis-rate-limiter.replenishRate: 10
    redis-rate-limiter.burstCapacity: 20
    redis-rate-limiter.requestedTokens: 1
    key-resolver: "#{@userKeyResolver}"
```

**Verify:**
```bash
# Gửi 25 requests liên tiếp với cùng một user
for i in {1..25}; do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9000/books; done
# Kỳ vọng: request 21-25 trả về 429 Too Many Requests
```

---

## Phase 3 — Downstream JWT Validation + RBAC

### 3.1 inventory-service — Thêm Resource Server từ đầu

**Hiện trạng:** `inventory-service` chưa có bất kỳ dependency hay config security nào.

**Step 1:** Thêm vào `build.gradle`:
```gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
testImplementation 'org.springframework.security:spring-security-test'
```

**Step 2:** Tạo `SecurityConfig.java` (reactive — giống `order-service`):
```java
package com.locpham.bookstore.inventoryservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http.authorizeExchange(
                        exchange ->
                                exchange.pathMatchers("/actuator/**")
                                        .permitAll()
                                        .pathMatchers(HttpMethod.GET, "/inventory/**")
                                        .permitAll()
                                        .anyExchange()
                                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }

    @Bean
    ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakJwtAuthoritiesConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
```

**Step 3:** Tạo `KeycloakJwtAuthoritiesConverter.java`:
```java
package com.locpham.bookstore.inventoryservice.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class KeycloakJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLES_CLAIM = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
```

**Step 4:** Thêm vào `config/inventory-service.yml`:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_URL:http://localhost:8080}/realms/PolarBookshop
```

---

### 3.2 search-service — Thêm Resource Server từ đầu

**Hiện trạng:** `search-service` chưa có bất kỳ dependency hay config security nào. Lưu ý: đang dùng Spring Boot `4.0.6`.

**Step 1:** Thêm vào `build.gradle`:
```gradle
implementation 'org.springframework.boot:spring-boot-starter-security'
implementation 'org.springframework.boot:spring-boot-starter-oauth2-resource-server'
testImplementation 'org.springframework.security:spring-security-test'
```

**Step 2:** Tạo `SecurityConfig.java` (reactive — WebFlux):
```java
package com.locpham.bookstore.searchservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http.authorizeExchange(
                        exchange ->
                                exchange.pathMatchers("/actuator/**")
                                        .permitAll()
                                        .pathMatchers(HttpMethod.GET, "/search/**")
                                        .permitAll()
                                        .anyExchange()
                                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }

    @Bean
    ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakJwtAuthoritiesConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
```

**Step 3:** Tạo `KeycloakJwtAuthoritiesConverter.java`:
```java
package com.locpham.bookstore.searchservice.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class KeycloakJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLES_CLAIM = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
```

**Step 4:** Thêm vào `config/search-service.yml`:
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_URL:http://localhost:8080}/realms/PolarBookshop
```

---

### 3.3 catalog-service — Hoàn thiện Resource Server

**Hiện trạng:** Đã có `SecurityConfig.java`, `KeycloakJwtAuthoritiesConverter`, `issuer-uri` config. Cần bổ sung method-level security.

**File:** `/Users/locpham/Desktop/Workspace/spring-native-bookstore/catalog-service/src/main/java/com/locpham/bookstore/catalogservice/config/SecurityConfig.java`

Thêm `@EnableMethodSecurity`:
```java
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    // ... existing code ...
}
```

**File:** `/Users/locpham/Desktop/Workspace/spring-native-bookstore/catalog-service/src/main/java/com/locpham/bookstore/catalogservice/web/BookController.java` (hoặc tương đương)

Thêm `@PreAuthorize` cho mutation endpoints:
```java
import org.springframework.security.access.prepost.PreAuthorize;

@PreAuthorize("hasRole('employee')")
@PostMapping("/books")
public ResponseEntity<Book> addBook(@RequestBody @Valid Book book) { ... }

@PreAuthorize("hasRole('employee')")
@PutMapping("/books/{isbn}")
public ResponseEntity<Book> updateBook(...) { ... }

@PreAuthorize("hasRole('employee')")
@DeleteMapping("/books/{isbn}")
public ResponseEntity<Void> deleteBook(...) { ... }
```

**Verify:**
```bash
cd catalog-service && ./gradlew test
# Hoặc test thủ công:
curl -i http://localhost:9001/books          # 200 (public)
curl -i -X POST http://localhost:9001/books   # 401 (no JWT)
```

### 3.4 order-service — Nâng cấp Resource Server (thiếu converter)

**Hiện trạng:** `SecurityConfig.java` quá đơn giản — chỉ `.authenticated()`, không có role check, không có custom authorities converter.

**File:** `/Users/locpham/Desktop/Workspace/spring-native-bookstore/order-service/src/main/java/com/locpham/bookstore/orderservice/bootstrap/config/SecurityConfig.java`

**Thay thế toàn bộ file:**

```java
package com.locpham.bookstore.orderservice.bootstrap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;

@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http.authorizeExchange(
                        exchange ->
                                exchange.pathMatchers("/actuator/**")
                                        .permitAll()
                                        .pathMatchers(HttpMethod.GET, "/orders/**")
                                        .authenticated()
                                        .pathMatchers(HttpMethod.POST, "/orders/**")
                                        .hasAnyRole("customer", "employee")
                                        .anyExchange()
                                        .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(
                        jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .build();
    }

    @Bean
    ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakJwtAuthoritiesConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
```

**File mới:** `/Users/locpham/Desktop/Workspace/spring-native-bookstore/order-service/src/main/java/com/locpham/bookstore/orderservice/bootstrap/config/KeycloakJwtAuthoritiesConverter.java`

```java
package com.locpham.bookstore.orderservice.bootstrap.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class KeycloakJwtAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String ROLES_CLAIM = "roles";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(ROLES_CLAIM);
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
    }
}
```

**Verify:**
```bash
cd order-service && ./gradlew spotlessApply
cd order-service && ./gradlew test
```

### 3.5 order-service — Đọc `X-User-Id` header trong `SubmitOrderService`

**Goal:** Khi order được tạo qua edge-service, `X-User-Id` header chứa username từ OIDC. Service cần đọc header này và gán vào `Order.createdBy` (hoặc trường tương đương).

**Cách làm:** Trong `SubmitOrderService` (hoặc controller/service tạo order), inject `ServerHttpRequest` hoặc dùng `@RequestHeader`:

```java
@PostMapping("/orders")
public Mono<Order> submitOrder(
        @RequestBody @Valid OrderRequest request,
        @RequestHeader(name = "X-User-Id", required = false) String userId) {
    return submitOrderService.submitOrder(request, userId);
}
```

**Hoặc** dùng `ReactiveSecurityContextHolder` để lấy từ authentication (ưu tiên cách này vì an toàn hơn — header có thể bị giả mạo nếu gọi trực tiếp):

```java
import org.springframework.security.core.context.ReactiveSecurityContextHolder;

public Mono<Order> submitOrder(OrderRequest request) {
    return ReactiveSecurityContextHolder.getContext()
            .map(ctx -> ctx.getAuthentication().getName())
            .defaultIfEmpty("anonymous")
            .flatMap(userId -> {
                // Tạo order với createdBy = userId
                return orderRepository.save(new Order(..., userId));
            });
}
```

**Verify:**
```bash
# Login qua edge-service → POST /orders
# Query DB: SELECT created_by FROM orders WHERE id = <new_order_id>
# Kỳ vọng: created_by = 'isabelle' (hoặc 'bjorn')
```

---

## Phase 4 — Security Testing

### 4.1 Unit test cho `SecurityConfig` trong edge-service

**File mới:** `/Users/locpham/Desktop/Workspace/spring-native-bookstore/edge-service/src/test/java/com/locpham/bookstore/edgeservice/security/SecurityConfigTest.java`

```java
package com.locpham.bookstore.edgeservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private WebTestClient webClient;

    @Test
    void unauthenticated_should_return_401_for_protected_routes() {
        webClient.get().uri("/orders").exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @WithMockUser(roles = "employee")
    void employee_can_access_post_books() {
        webClient.post().uri("/books").exchange()
                .expectStatus().isNotFound(); // 404 = passed auth, route not mocked
    }

    @Test
    @WithMockUser(roles = "customer")
    void customer_cannot_post_books() {
        webClient.post().uri("/books").exchange()
                .expectStatus().isForbidden();
    }
}
```

**Note:** `@WebFluxTest` cần mock các routes. Thực tế có thể dùng `@SpringBootTest(webEnvironment = RANDOM_PORT)` thay thế.

### 4.2 Keycloak Testcontainers cho Integration Tests

**Dependency cần thêm vào `edge-service/build.gradle`:**
```gradle
testImplementation 'com.github.dasniko:testcontainers-keycloak:3.4.0'
```

**File mới:** `/Users/locpham/Desktop/Workspace/spring-native-bookstore/edge-service/src/test/java/com/locpham/bookstore/edgeservice/security/KeycloakIntegrationTest.java`

```java
package com.locpham.bookstore.edgeservice.security;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class KeycloakIntegrationTest {

    @Container
    static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:24.0")
            .withRealmImportFile("test-realm.json");

    @Autowired
    WebTestClient webClient;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri",
                () -> keycloak.getAuthServerUrl() + "realms/PolarBookshop");
    }

    @Test
    void healthEndpoint_should_be_public() {
        webClient.get().uri("/actuator/health").exchange()
                .expectStatus().isOk();
    }
}
```

**File resource:** `/Users/locpham/Desktop/Workspace/spring-native-bookstore/edge-service/src/test/resources/test-realm.json`
> Export realm JSON từ Keycloak admin console → lưu vào đây. Hoặc tạo programmatically trong test setup.

### 4.3 Verify downstream protection (catalog-service bypass edge)

```bash
# Không qua edge-service, gọi trực tiếp catalog-service
curl -i http://localhost:9001/books          # 200 (public GET)
curl -i -X POST http://localhost:9001/books   # 401 (không có JWT)

# Với JWT hợp lệ (lấy từ Keycloak qua edge-service login)
TOKEN=$(curl -s -X POST ... ) # hoặc lấy từ browser cookie/Redis
curl -i -X POST -H "Authorization: Bearer $TOKEN" http://localhost:9001/books
# 404/201 = passed JWT validation
```

---

## Phase 5 — Rate Limiting & Observability

### 5.1 Per-User Rate Limiting (đã implement ở 2.4)

**Double-check:** `edge-service` đã chạy Redis, `userKeyResolver` hoạt động.

```bash
# Start Redis
redis-server

# Start edge-service
make run-edge

# Script test rate limit
for i in {1..25}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -b "SESSION=<cookie_from_login>" \
    http://localhost:9000/books
done
```

### 5.2 Security Event Logging

**File:** `edge-service` — thêm `SecurityEventListener` (nếu chưa có):

```java
@Component
@Slf4j
public class SecurityEventListener {

    @EventListener
    public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
        log.info("Authentication success: {}", event.getAuthentication().getName());
    }

    @EventListener
    public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
        log.warn("Authentication failure: {}", event.getException().getMessage());
    }
}
```

---

## ✅ Acceptance Criteria (Definition of Done)

| # | Criteria | Verify Command |
|---|----------|--------------|
| 1 | `GET /books` qua edge-service → 200, không cần login | `curl -s -o /dev/null -w "%{http_code}" http://localhost:9000/books` |
| 2 | `POST /books` qua edge-service, chưa login → 401 | `curl -i -X POST http://localhost:9000/books` |
| 3 | `POST /books` với user `customer` → 403 | Login bằng `isabelle` → POST `/books` |
| 4 | `POST /books` với user `employee` → pass auth (404/201) | Login bằng `bjorn` → POST `/books` |
| 5 | `POST /orders` với `customer` → pass auth | Login bằng `isabelle` → POST `/orders` |
| 6 | Gọi trực tiếp `catalog-service:9001/books` POST không có JWT → 401 | `curl -i -X POST http://localhost:9001/books` |
| 7 | Gọi trực tiếp `inventory-service:9004/inventory` POST không có JWT → 401 | `curl -i -X POST http://localhost:9004/inventory` |
| 8 | Gọi trực tiếp `search-service:9005/search` POST không có JWT → 401 | `curl -i -X POST http://localhost:9005/search` |
| 9 | Response headers có `Content-Security-Policy` | `curl -I http://localhost:9000/books` |
| 10 | Rate limit: 25 requests liên tiếp → request 21+ trả 429 | Script loop ở trên |
| 11 | Logout → session Redis bị xóa, Keycloak session bị destroy | Login → logout → dùng cookie cũ → 401 |
| 12 | Order được tạo qua edge-service có `createdBy` = username | Query DB `polardb_order` |

---

## 📂 Files Touched Summary

| Service | File | Action |
|---------|------|--------|
| edge-service | `security/SecurityConfig.java` | Modify — add RBAC, headers, authoritiesMapper |
| edge-service | `security/UserIdHeaderFilter.java` | Create — propagate `X-User-Id` |
| edge-service | `security/RateLimiterConfig.java` | Create/modify — per-user key resolver |
| edge-service | `build.gradle` | Add `testcontainers-keycloak` |
| inventory-service | `build.gradle` | Add `oauth2-resource-server`, `security` |
| inventory-service | `config/SecurityConfig.java` | Create |
| inventory-service | `config/KeycloakJwtAuthoritiesConverter.java` | Create |
| inventory-service | `config/inventory-service.yml` | Add `issuer-uri` |
| search-service | `build.gradle` | Add `oauth2-resource-server`, `security` |
| search-service | `config/SecurityConfig.java` | Create |
| search-service | `config/KeycloakJwtAuthoritiesConverter.java` | Create |
| search-service | `config/search-service.yml` | Add `issuer-uri` |
| edge-service | `src/test/resources/test-realm.json` | Create — Keycloak realm import |
| edge-service | `src/test/java/.../SecurityConfigTest.java` | Create |
| edge-service | `src/test/java/.../KeycloakIntegrationTest.java` | Create |
| config | `edge-service.yml` | Modify — add `key-resolver` to RateLimiter |
| catalog-service | `config/SecurityConfig.java` | Modify — add `@EnableMethodSecurity` |
| catalog-service | `web/BookController.java` | Modify — add `@PreAuthorize` |
| order-service | `bootstrap/config/SecurityConfig.java` | Modify — full RBAC + converter |
| order-service | `bootstrap/config/KeycloakJwtAuthoritiesConverter.java` | Create |
| order-service | `application/service/SubmitOrderService.java` | Modify — read `X-User-Id` or principal name |

---

## 📚 Further Reading

- **OAuth2 / OIDC specs (must-read trước khi code):**
  - [RFC 6749 — OAuth 2.0](https://datatracker.ietf.org/doc/html/rfc6749)
  - [RFC 7636 — PKCE](https://datatracker.ietf.org/doc/html/rfc7636)
  - [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- **Spring Security:**
  - [Spring Security — OAuth2 Login (Reactive)](https://docs.spring.io/spring-security/reference/reactive/oauth2/login/index.html)
  - [Spring Security — OAuth2 Resource Server (JWT)](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
  - [Spring Security — Reactive Method Security](https://docs.spring.io/spring-security/reference/reactive/authorization/method.html)
  - [Spring Security — Token Relay filter](https://docs.spring.io/spring-cloud-gateway/reference/spring-cloud-gateway/global-filters.html#the-tokenrelay-filter)
- **Keycloak:**
  - [Keycloak — Server Administration Guide](https://www.keycloak.org/docs/latest/server_admin/)
  - [Keycloak Testcontainers (Java)](https://github.com/dasniko/testcontainers-keycloak)
- **BFF Pattern:**
  - [OAuth2 BFF pattern — IETF draft](https://datatracker.ietf.org/doc/html/draft-bertocci-oauth2-tmi-bff)
  - [Auth0 — BFF for SPAs](https://auth0.com/blog/the-backend-for-frontend-pattern-bff/)
