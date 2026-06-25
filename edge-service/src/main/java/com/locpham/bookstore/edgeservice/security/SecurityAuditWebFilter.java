package com.locpham.bookstore.edgeservice.security;

import java.security.Principal;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
class SecurityAuditWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditWebFilter.class);
    private static final Set<HttpMethod> MUTATING_METHODS =
            Set.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return chain.filter(exchange)
                .then(Mono.defer(() -> logIfRelevant(exchange, null)))
                .onErrorResume(
                        error ->
                                Mono.defer(() -> logIfRelevant(exchange, error))
                                        .then(Mono.error(error)));
    }

    private Mono<Void> logIfRelevant(ServerWebExchange exchange, Throwable error) {
        HttpStatusCode status = exchange.getResponse().getStatusCode();
        boolean mutating = MUTATING_METHODS.contains(exchange.getRequest().getMethod());
        boolean securityOutcome =
                status != null && (status.value() == 401 || status.value() == 403);

        if (!mutating && !securityOutcome && error == null) {
            return Mono.empty();
        }

        return exchange.getPrincipal()
                .ofType(Authentication.class)
                .filter(Authentication::isAuthenticated)
                .map(Principal::getName)
                .defaultIfEmpty("anonymous")
                .doOnNext(
                        username ->
                                log.info(
                                        "security_audit method={} path={} user={} status={} outcome={}",
                                        exchange.getRequest().getMethod(),
                                        exchange.getRequest()
                                                .getPath()
                                                .pathWithinApplication()
                                                .value(),
                                        username,
                                        status != null ? status.value() : "unknown",
                                        error == null ? "completed" : "error"))
                .then();
    }
}
