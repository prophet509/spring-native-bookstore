package com.locpham.bookstore.edgeservice.security;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class UserIdHeaderFilter implements GlobalFilter, Ordered {

    public static final String USER_ID_HEADER = "X-User-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal()
                .flatMap(
                        principal -> {
                            if (principal instanceof OAuth2AuthenticationToken token) {
                                String userId = token.getPrincipal().getName();
                                ServerWebExchange mutatedExchange =
                                        exchange.mutate()
                                                .request(
                                                        request ->
                                                                request.header(
                                                                        USER_ID_HEADER, userId))
                                                .build();
                                return chain.filter(mutatedExchange);
                            }
                            return chain.filter(exchange);
                        })
                .switchIfEmpty(chain.filter(exchange));
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
