package com.locpham.bookstore.edgeservice.security;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimiterConfig {

    @Bean
    KeyResolver userKeyResolver() {
        return exchange ->
                exchange.getPrincipal()
                        .map(principal -> principal.getName())
                        .defaultIfEmpty("anonymous");
    }
}
