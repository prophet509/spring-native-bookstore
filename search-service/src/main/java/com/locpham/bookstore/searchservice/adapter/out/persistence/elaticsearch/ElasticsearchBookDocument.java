package com.locpham.bookstore.searchservice.adapter.out.persistence.elaticsearch;

import com.locpham.bookstore.searchservice.domain.BookDocument;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

@Document(indexName = "books", createIndex = false)
public record ElasticsearchBookDocument(
        @Id String isbn,
        @MultiField(
                        mainField = @Field(type = FieldType.Text, analyzer = "english"),
                        otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword))
                String title,
        @Field(type = FieldType.Text, analyzer = "english") String author,
        @Field(type = FieldType.Scaled_Float, scalingFactor = 100) Double price,
        @Field(type = FieldType.Keyword) String publisher) {

    public static ElasticsearchBookDocument fromDomain(BookDocument doc) {
        return new ElasticsearchBookDocument(
                doc.isbn(), doc.title(), doc.author(), doc.price(), doc.publisher());
    }

    public BookDocument toDomain() {
        return new BookDocument(isbn, title, author, price, publisher);
    }
}
