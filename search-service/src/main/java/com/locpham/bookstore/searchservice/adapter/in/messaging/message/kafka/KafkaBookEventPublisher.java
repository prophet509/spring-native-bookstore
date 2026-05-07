package com.locpham.bookstore.searchservice.adapter.in.messaging.message.kafka;

import com.locpham.bookstore.searchservice.application.out.message.BookEventPublisher;

import java.awt.print.Book;

public class KafkaBookEventPublisher implements BookEventPublisher {


    @Override
    public void publishBookCreated(Book book) {

    }

    @Override
    public void publishBookUpdated(Book book) {

    }

    @Override
    public void publishBookDeleted(String isbn) {

    }
}
