package com.locpham.bookstore.catalogservice.adapter.out.persistence;

import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

/** Spring Data JDBC repository for {@link OutboxEvent} rows. */
public interface SpringDataOutboxRepository extends CrudRepository<OutboxEvent, UUID> {}
