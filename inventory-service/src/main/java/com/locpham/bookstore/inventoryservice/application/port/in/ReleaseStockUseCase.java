package com.locpham.bookstore.inventoryservice.application.port.in;

import reactor.core.publisher.Mono;

public interface ReleaseStockUseCase {
    Mono<Void> releaseForOrder(Long orderId);
}
