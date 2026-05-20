package com.locpham.bookstore.inventoryservice.application.port.out;

import com.locpham.bookstore.inventoryservice.domain.Reservation;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ReservationPort {
    Flux<Reservation> findByOrderId(Long orderId);

    Mono<Reservation> save(Reservation reservation);
}
