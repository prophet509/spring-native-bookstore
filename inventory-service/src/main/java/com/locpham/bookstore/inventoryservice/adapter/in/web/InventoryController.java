package com.locpham.bookstore.inventoryservice.adapter.in.web;

import com.locpham.bookstore.inventoryservice.adapter.in.web.dto.StockAdjustmentRequest;
import com.locpham.bookstore.inventoryservice.application.port.in.ManageStockUseCase;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import jakarta.validation.Valid;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private static final Logger log = LoggerFactory.getLogger(InventoryController.class);

    private final ManageStockUseCase manageStockUseCase;

    public InventoryController(ManageStockUseCase manageStockUseCase) {
        this.manageStockUseCase = manageStockUseCase;
    }

    @GetMapping("/{isbn}")
    public Mono<InventoryItem> getStock(@PathVariable String isbn) {
        log.debug("GET /inventory/{}", isbn);
        return manageStockUseCase.queryStock(isbn);
    }

    @GetMapping
    public Flux<InventoryItem> getStocks(@RequestParam List<String> isbn) {
        log.debug("GET /inventory?isbn={}", isbn);
        return Flux.fromIterable(isbn).flatMap(manageStockUseCase::queryStock);
    }

    @PostMapping("/{isbn}/adjust")
    public Mono<InventoryItem> adjustStock(
            @PathVariable String isbn, @RequestBody @Valid StockAdjustmentRequest request) {
        log.info("POST /inventory/{}/adjust delta={}", isbn, request.delta());
        if (request.delta() >= 0) {
            return manageStockUseCase.addStock(isbn, request.delta());
        } else {
            return manageStockUseCase.reduceStock(isbn, -request.delta());
        }
    }
}
