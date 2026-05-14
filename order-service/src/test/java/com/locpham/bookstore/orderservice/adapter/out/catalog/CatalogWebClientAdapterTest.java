package com.locpham.bookstore.orderservice.adapter.out.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.locpham.bookstore.orderservice.domain.exception.BookNotFoundException;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

class CatalogWebClientAdapterTest {

    private CatalogWebClientAdapter catalogWebClientAdapter;
    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        var webClientBuilder = WebClient.builder();
        catalogWebClientAdapter =
                new CatalogWebClientAdapter(webClientBuilder, mockWebServer.url("/").toString());
    }

    @Test
    void loadBook() {
        var mockResponse =
                new MockResponse()
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody(
                                """
                       {
                            "isbn": "1234567890",
                            "title": "Book",
                            "price": 9.99
                        }
                        """);

        mockWebServer.enqueue(mockResponse);

        var bookOld = catalogWebClientAdapter.loadBook("1234567890").block();
        assertEquals("1234567890", bookOld.isbn());
        assertEquals("Book", bookOld.title());
        assertEquals(9.99, bookOld.price());
    }

    @Test
    void loadBookNotFound() {
        var mockResponse =
                new MockResponse()
                        .setResponseCode(404)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody("");

        mockWebServer.enqueue(mockResponse);

        var exception = assertThrows(
                BookNotFoundException.class,
                () -> catalogWebClientAdapter.loadBook("1234567890").block());
        assertEquals("Book with ISBN '1234567890' not found in catalog", exception.getMessage());
    }

    @Test
    void loadBookError() {
        var mockResponse =
                new MockResponse()
                        .setResponseCode(500)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody("");

        mockWebServer.enqueue(mockResponse);

        var exception = assertThrows(
                BookNotFoundException.class,
                () -> catalogWebClientAdapter.loadBook("1234567890").block());
        assertEquals("Book with ISBN '1234567890' not found in catalog", exception.getMessage());
    }
}
