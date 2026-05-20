package com.locpham.bookstore.inventoryservice.application.service;

import com.locpham.bookstore.inventoryservice.application.port.in.ManageStockUseCase;
import com.locpham.bookstore.inventoryservice.application.port.out.InventoryPort;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class StockManagementService implements ManageStockUseCase {

    private static final Logger log = LoggerFactory.getLogger(StockManagementService.class);

    private final InventoryPort inventoryPort;

    public StockManagementService(InventoryPort inventoryPort) {
        this.inventoryPort = inventoryPort;
    }

    @Override
    public Mono<InventoryItem> addStock(String isbn, int quantity) {
        log.info("Adding stock isbn={} quantity={}", isbn, quantity);
        return inventoryPort
                .findByIsbn(isbn)
                .defaultIfEmpty(InventoryItem.create(isbn, 0))
                .map(item -> item.adjust(quantity))
                .flatMap(inventoryPort::save)
                .doOnSuccess(
                        item ->
                                log.info(
                                        "Stock added isbn={} newAvailable={} reserved={}",
                                        isbn,
                                        item.availableQuantity(),
                                        item.reservedQuantity()))
                .doOnError(
                        e ->
                                log.error(
                                        "Failed to add stock isbn={} quantity={}",
                                        isbn,
                                        quantity,
                                        e));
    }

    @Override
    public Mono<InventoryItem> reduceStock(String isbn, int quantity) {
        log.info("Reducing stock isbn={} quantity={}", isbn, quantity);
        return inventoryPort
                .findByIsbn(isbn)
                .switchIfEmpty(
                        Mono.error(
                                new IllegalArgumentException(
                                        "Inventory not found for ISBN: " + isbn)))
                .map(item -> item.adjust(-quantity))
                .flatMap(inventoryPort::save)
                .doOnSuccess(
                        item ->
                                log.info(
                                        "Stock reduced isbn={} newAvailable={} reserved={}",
                                        isbn,
                                        item.availableQuantity(),
                                        item.reservedQuantity()))
                .doOnError(
                        e ->
                                log.error(
                                        "Failed to reduce stock isbn={} quantity={}",
                                        isbn,
                                        quantity,
                                        e));
    }

    @Override
    public Mono<InventoryItem> queryStock(String isbn) {
        log.debug("Querying stock isbn={}", isbn);
        return inventoryPort.findByIsbn(isbn);
    }
}
