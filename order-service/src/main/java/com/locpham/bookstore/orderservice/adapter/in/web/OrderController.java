package com.locpham.bookstore.orderservice.adapter.in.web;

import com.locpham.bookstore.orderservice.adapter.in.web.dto.OrderRequest;
import com.locpham.bookstore.orderservice.adapter.in.web.mapper.OrderWebMapper;
import com.locpham.bookstore.orderservice.application.port.in.GetOrdersUseCase;
import com.locpham.bookstore.orderservice.application.port.in.SubmitOrderUseCase;
import com.locpham.bookstore.orderservice.domain.model.Order;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final SubmitOrderUseCase submitOrderUseCase;
    private final GetOrdersUseCase getOrdersUseCase;

    public OrderController(
            SubmitOrderUseCase submitOrderUseCase, GetOrdersUseCase getOrdersUseCase) {
        this.submitOrderUseCase = submitOrderUseCase;
        this.getOrdersUseCase = getOrdersUseCase;
    }

    @GetMapping
    public Flux<Order> getAllOrders(@AuthenticationPrincipal Jwt jwt) {
        return getOrdersUseCase.getOrders(OrderWebMapper.toQuery(jwt.getSubject()));
    }

    @PostMapping
    public Mono<Order> submitOrder(
            @RequestBody @Valid OrderRequest orderRequest, @AuthenticationPrincipal Jwt jwt) {
        return submitOrderUseCase.submitOrder(
                OrderWebMapper.toCommand(orderRequest, jwt.getSubject()));
    }
}
