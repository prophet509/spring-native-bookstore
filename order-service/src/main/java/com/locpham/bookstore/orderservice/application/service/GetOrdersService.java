package com.locpham.bookstore.orderservice.application.service;

import com.locpham.bookstore.orderservice.application.port.in.GetOrdersUseCase;
import com.locpham.bookstore.orderservice.application.port.out.OrderQueryPort;
import com.locpham.bookstore.orderservice.application.query.GetOrdersQuery;
import com.locpham.bookstore.orderservice.domain.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class GetOrdersService implements GetOrdersUseCase {

    private static final Logger log = LoggerFactory.getLogger(GetOrdersService.class);

    private final OrderQueryPort orderQueryPort;

    public GetOrdersService(OrderQueryPort orderQueryPort) {
        this.orderQueryPort = orderQueryPort;
    }

    @Override
    public Flux<Order> getOrders(GetOrdersQuery query) {
        log.debug("Fetching orders user={}", query.userId());
        return orderQueryPort
                .findByCreatedBy(query.userId())
                .doOnComplete(() -> log.debug("Completed fetching orders user={}", query.userId()))
                .doOnError(
                        e ->
                                log.error(
                                        "Failed to fetch orders user={} error={}",
                                        query.userId(),
                                        e.getMessage(),
                                        e));
    }
}
