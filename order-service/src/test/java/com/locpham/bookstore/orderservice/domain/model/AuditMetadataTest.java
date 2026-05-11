package com.locpham.bookstore.orderservice.domain.model;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuditMetadataTest {

    @Test
    void init_shouldCreateAuditMetadataWithCurrentTimestamps() {
        var before = Instant.now();
        var audit = AuditMetadata.init();

        assertNotNull(audit.createdDate());
        assertNotNull(audit.lastModifiedDate());
        assertNull(audit.createdBy());
        assertNull(audit.lastModifiedBy());

        // Verify timestamps are close to current time (within 1 second)
        assertTrue(Duration.between(before, audit.createdDate()).abs().getSeconds() < 1);
        assertTrue(Duration.between(before, audit.lastModifiedDate()).abs().getSeconds() < 1);
    }

    @Test
    void update_shouldKeepCreatedDateAndUpdateLastModifiedDate() throws InterruptedException {
        var original = AuditMetadata.init();
        Thread.sleep(1);
        var updated = original.update();

        assertEquals(original.createdDate(), updated.createdDate());
        assertNotEquals(original.lastModifiedDate(), updated.lastModifiedDate());
        assertNotNull(updated.lastModifiedDate());
    }

    @Test
    void equals_shouldWorkCorrectly() {
        var audit1 = AuditMetadata.init();
        var audit2 =
                new AuditMetadata(
                        audit1.createdDate(),
                        audit1.lastModifiedDate(),
                        audit1.createdBy(),
                        audit1.lastModifiedBy());

        assertEquals(audit1, audit2);
        assertEquals(audit1.hashCode(), audit2.hashCode());
    }
}
