package com.locpham.bookstore.orderservice.adapter.in.web;

import java.nio.charset.StandardCharsets;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class IdempotencyWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyWebFilter.class);
    static final String IDEMPOTENCY_HEADER = "Idempotency-Key";
    private static final String CLAIM_PREFIX = "order:idem:";
    private static final String RESULT_PREFIX = "order:idem:result:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public IdempotencyWebFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!isOrderPost(exchange)) {
            return chain.filter(exchange);
        }

        var idempotencyKey = exchange.getRequest().getHeaders().getFirst(IDEMPOTENCY_HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return chain.filter(exchange);
        }

        var claimKey = CLAIM_PREFIX + idempotencyKey;
        var resultKey = RESULT_PREFIX + idempotencyKey;

        return claimIdempotencyKey(claimKey)
                .flatMap(
                        claimed -> {
                            if (Boolean.FALSE.equals(claimed)) {
                                return handleAlreadyClaimed(exchange, resultKey);
                            }
                            return handleNewRequest(exchange, chain, resultKey);
                        })
                .onErrorResume(
                        e -> {
                            log.warn("Idempotency Redis error, bypassing: {}", e.getMessage());
                            return chain.filter(exchange);
                        });
    }

    private Mono<Boolean> claimIdempotencyKey(String claimKey) {
        return redisTemplate
                .opsForValue()
                .setIfAbsent(claimKey, "1", java.time.Duration.ofHours(24));
    }

    private Mono<Void> handleAlreadyClaimed(ServerWebExchange exchange, String resultKey) {
        return redisTemplate
                .opsForValue()
                .get(resultKey)
                .flatMap(
                        cachedJson -> {
                            exchange.getResponse().setStatusCode(HttpStatus.OK);
                            exchange.getResponse()
                                    .getHeaders()
                                    .setContentType(MediaType.APPLICATION_JSON);
                            exchange.getResponse().getHeaders().add("X-Idempotency-Replay", "true");
                            byte[] bytes = cachedJson.getBytes(StandardCharsets.UTF_8);
                            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                            return exchange.getResponse().writeWith(Mono.just(buffer));
                        })
                .switchIfEmpty(chainWithoutIdempotency(exchange));
    }

    private Mono<Void> chainWithoutIdempotency(ServerWebExchange exchange) {
        log.warn("Idempotency key claimed but no cached result, proceeding without cache");
        return Mono.defer(
                () -> {
                    throw new IllegalStateException("No cached result for claimed idempotency key");
                });
    }

    private Mono<Void> handleNewRequest(
            ServerWebExchange exchange, WebFilterChain chain, String resultKey) {
        var capturingResponse =
                new BodyCapturingResponseDecorator(
                        exchange.getResponse(), resultKey, redisTemplate);
        var wrappedExchange = exchange.mutate().response(capturingResponse).build();
        return chain.filter(wrappedExchange);
    }

    private boolean isOrderPost(ServerWebExchange exchange) {
        var method = exchange.getRequest().getMethod();
        var path = exchange.getRequest().getPath().value();
        return "POST".equalsIgnoreCase(method.name()) && path.startsWith("/orders");
    }

    static class BodyCapturingResponseDecorator extends ServerHttpResponseDecorator {

        private final String resultKey;
        private final ReactiveRedisTemplate<String, String> redisTemplate;
        private final Logger log = LoggerFactory.getLogger(BodyCapturingResponseDecorator.class);

        BodyCapturingResponseDecorator(
                ServerHttpResponse delegate,
                String resultKey,
                ReactiveRedisTemplate<String, String> redisTemplate) {
            super(delegate);
            this.resultKey = resultKey;
            this.redisTemplate = redisTemplate;
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            var buffers =
                    Flux.from(body)
                            .collectList()
                            .flatMap(
                                    dataBuffers -> {
                                        var sb = new StringBuilder();
                                        for (DataBuffer buffer : dataBuffers) {
                                            byte[] bytes = new byte[buffer.readableByteCount()];
                                            buffer.read(bytes);
                                            sb.append(new String(bytes, StandardCharsets.UTF_8));
                                        }
                                        return redisTemplate
                                                .opsForValue()
                                                .set(
                                                        resultKey,
                                                        sb.toString(),
                                                        java.time.Duration.ofHours(24))
                                                .onErrorResume(
                                                        e -> {
                                                            log.warn(
                                                                    "Failed to cache idempotency result: {}",
                                                                    e.getMessage());
                                                            return Mono.empty();
                                                        })
                                                .thenReturn(dataBuffers);
                                    });
            return super.writeWith(buffers.flatMapMany(Flux::fromIterable));
        }
    }
}
