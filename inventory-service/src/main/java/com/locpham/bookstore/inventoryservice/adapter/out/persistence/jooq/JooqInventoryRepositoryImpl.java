package com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq;

import static com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq.generated.tables.Inventory.INVENTORY;

import com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq.generated.tables.records.InventoryRecord;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryPort;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import java.util.List;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class JooqInventoryRepositoryImpl implements InventoryPort {

    private static final Logger log = LoggerFactory.getLogger(JooqInventoryRepositoryImpl.class);

    private final DSLContext dsl;

    public JooqInventoryRepositoryImpl(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Mono<InventoryItem> findByIsbn(String isbn) {
        log.debug("findByIsbn isbn={}", isbn);
        return Mono.from(dsl.selectFrom(INVENTORY).where(INVENTORY.ISBN.eq(isbn)))
                .map(JooqInventoryMapper::toDomain);
    }

    @Override
    public Flux<InventoryItem> findAllByIsbn(List<String> isbns) {
        log.debug("findAllByIsbn isbns={}", isbns);
        return Flux.from(dsl.selectFrom(INVENTORY).where(INVENTORY.ISBN.in(isbns)))
                .map(JooqInventoryMapper::toDomain);
    }

    @Override
    public Mono<InventoryItem> save(InventoryItem item) {
        InventoryRecord record = JooqInventoryMapper.toRecord(item);
        if (item.id() == null) {
            log.debug(
                    "Inserting new inventory isbn={} available={}",
                    item.isbn(),
                    item.availableQuantity());
            return Mono.from(
                            dsl.insertInto(INVENTORY)
                                    .set(INVENTORY.ISBN, record.getIsbn())
                                    .set(
                                            INVENTORY.AVAILABLE_QUANTITY,
                                            record.getAvailableQuantity())
                                    .set(INVENTORY.RESERVED_QUANTITY, record.getReservedQuantity())
                                    .set(INVENTORY.VERSION, record.getVersion())
                                    .returning(INVENTORY.fields()))
                    .map(JooqInventoryMapper::toDomain)
                    .doOnSuccess(
                            saved ->
                                    log.debug(
                                            "Inserted inventory id={} isbn={}",
                                            saved.id(),
                                            saved.isbn()));
        } else {
            // Optimistic lock: match current version, bump version on successful update.
            long nextVersion = item.version() + 1;
            log.debug(
                    "Updating inventory id={} isbn={} version={}->{} available={} reserved={}",
                    item.id(),
                    item.isbn(),
                    item.version(),
                    nextVersion,
                    item.availableQuantity(),
                    item.reservedQuantity());
            return Mono.from(
                            dsl.update(INVENTORY)
                                    .set(
                                            INVENTORY.AVAILABLE_QUANTITY,
                                            record.getAvailableQuantity())
                                    .set(INVENTORY.RESERVED_QUANTITY, record.getReservedQuantity())
                                    .set(INVENTORY.VERSION, nextVersion)
                                    .where(INVENTORY.ID.eq(item.id()))
                                    .and(INVENTORY.VERSION.eq(item.version()))
                                    .returning(INVENTORY.fields()))
                    .map(JooqInventoryMapper::toDomain)
                    .switchIfEmpty(
                            Mono.defer(
                                    () -> {
                                        log.warn(
                                                "Optimistic lock failure id={} isbn={} expectedVersion={}",
                                                item.id(),
                                                item.isbn(),
                                                item.version());
                                        return Mono.error(
                                                new OptimisticLockingFailureException(
                                                        "Inventory update lost optimistic lock for id "
                                                                + item.id()
                                                                + " (expected version "
                                                                + item.version()
                                                                + ")"));
                                    }));
        }
    }

    @Override
    public Flux<InventoryItem> saveAll(List<InventoryItem> items) {
        log.debug("saveAll count={}", items.size());
        return Flux.fromIterable(items).flatMap(this::save);
    }
}
