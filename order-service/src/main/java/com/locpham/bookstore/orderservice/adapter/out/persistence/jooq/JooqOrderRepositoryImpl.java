package com.locpham.bookstore.orderservice.adapter.out.persistence.jooq;

import static com.locpham.bookstore.orderservice.adapter.out.persistence.jooq.generated.tables.Orders.ORDERS;

import com.locpham.bookstore.orderservice.application.port.out.OrderCommandPort;
import com.locpham.bookstore.orderservice.application.port.out.OrderQueryPort;
import com.locpham.bookstore.orderservice.domain.model.Order;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class JooqOrderRepositoryImpl implements OrderCommandPort, OrderQueryPort {

    private final DSLContext dsl;

    public JooqOrderRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Mono<Order> save(Order order) {
        var record = JooqOrderMapper.toRecord(order);

        // Use Flux.from(...).singleOrEmpty() (not Mono.from) so the jOOQ publisher completes
        // naturally. Mono.from cancels after the first row, which makes r2dbc-pool reclaim the
        // transactional connection mid-transaction -> the following outbox insert then fails with
        // "Connection handle already closed".
        if (order.id() == null) {
            return Flux.from(
                            dsl.insertInto(ORDERS)
                                    .set(ORDERS.BOOK_ISBN, record.getBookIsbn())
                                    .set(ORDERS.BOOK_NAME, record.getBookName())
                                    .set(ORDERS.BOOK_PRICE, record.getBookPrice())
                                    .set(ORDERS.QUANTITY, record.getQuantity())
                                    .set(ORDERS.STATUS, record.getStatus())
                                    .set(ORDERS.CREATED_DATE, record.getCreatedDate())
                                    .set(ORDERS.LAST_MODIFIED_DATE, record.getLastModifiedDate())
                                    .set(ORDERS.CREATED_BY, record.getCreatedBy())
                                    .set(ORDERS.LAST_MODIFIED_BY, record.getLastModifiedBy())
                                    .set(ORDERS.VERSION, record.getVersion())
                                    .returning(ORDERS.fields()))
                    .singleOrEmpty()
                    .map(JooqOrderMapper::toDomain);
        } else {
            return Flux.from(
                            dsl.update(ORDERS)
                                    .set(ORDERS.STATUS, record.getStatus())
                                    .set(ORDERS.LAST_MODIFIED_DATE, record.getLastModifiedDate())
                                    .set(ORDERS.LAST_MODIFIED_BY, record.getLastModifiedBy())
                                    .set(ORDERS.VERSION, record.getVersion())
                                    .where(ORDERS.ID.eq(order.id()))
                                    .and(ORDERS.VERSION.eq(order.version() - 1))
                                    .returning(ORDERS.fields()))
                    .singleOrEmpty()
                    .map(JooqOrderMapper::toDomain);
        }
    }

    @Override
    public Flux<Order> findAll() {
        return Flux.from(dsl.selectFrom(ORDERS)).map(JooqOrderMapper::toDomain);
    }

    @Override
    public Mono<Order> findById(Long id) {
        return Mono.from(dsl.selectFrom(ORDERS).where(ORDERS.ID.eq(id)))
                .map(JooqOrderMapper::toDomain);
    }

    @Override
    public Flux<Order> findByCreatedBy(String createdBy) {
        return Flux.from(dsl.selectFrom(ORDERS).where(ORDERS.CREATED_BY.eq(createdBy)))
                .map(JooqOrderMapper::toDomain);
    }
}
