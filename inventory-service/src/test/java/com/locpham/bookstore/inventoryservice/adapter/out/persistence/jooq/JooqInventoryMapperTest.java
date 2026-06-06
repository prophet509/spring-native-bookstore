package com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq;

import static org.assertj.core.api.Assertions.assertThat;

import com.locpham.bookstore.inventoryservice.adapter.out.persistence.jooq.generated.tables.records.InventoryRecord;
import com.locpham.bookstore.inventoryservice.domain.InventoryItem;
import org.junit.jupiter.api.Test;

class JooqInventoryMapperTest {

    @Test
    void toRecordMapsAllFields() {
        var item = new InventoryItem(7L, "1234567890", 10, 3, 2L);

        InventoryRecord record = JooqInventoryMapper.toRecord(item);

        assertThat(record.getId()).isEqualTo(7L);
        assertThat(record.getIsbn()).isEqualTo("1234567890");
        assertThat(record.getAvailableQuantity()).isEqualTo(10);
        assertThat(record.getReservedQuantity()).isEqualTo(3);
        assertThat(record.getVersion()).isEqualTo(2L);
    }

    @Test
    void toDomainMapsAllFields() {
        var record =
                new InventoryRecord()
                        .setId(7L)
                        .setIsbn("1234567890")
                        .setAvailableQuantity(10)
                        .setReservedQuantity(3)
                        .setVersion(2L);

        InventoryItem item = JooqInventoryMapper.toDomain(record);

        assertThat(item.id()).isEqualTo(7L);
        assertThat(item.isbn()).isEqualTo("1234567890");
        assertThat(item.availableQuantity()).isEqualTo(10);
        assertThat(item.reservedQuantity()).isEqualTo(3);
        assertThat(item.version()).isEqualTo(2L);
    }
}
