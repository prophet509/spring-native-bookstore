package com.locpham.bookstore.inventoryservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InventoryDecisionTest {

    @Test
    void reservedHasReservedStatusAndNoReason() {
        var decision = InventoryDecision.reserved(1L);

        assertThat(decision.orderId()).isEqualTo(1L);
        assertThat(decision.status()).isEqualTo(InventoryDecision.DecisionStatus.RESERVED);
        assertThat(decision.reason()).isNull();
    }

    @Test
    void rejectedCarriesReason() {
        var decision = InventoryDecision.rejected(2L, "out of stock");

        assertThat(decision.status()).isEqualTo(InventoryDecision.DecisionStatus.REJECTED);
        assertThat(decision.reason()).isEqualTo("out of stock");
    }

    @Test
    void nullOrderIdRejected() {
        assertThatThrownBy(() -> InventoryDecision.reserved(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order ID");
    }

    @Test
    void nullStatusRejected() {
        assertThatThrownBy(() -> new InventoryDecision(1L, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Status");
    }
}
