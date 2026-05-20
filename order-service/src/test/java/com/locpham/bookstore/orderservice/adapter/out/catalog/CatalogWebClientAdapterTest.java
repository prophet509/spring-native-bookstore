package com.locpham.bookstore.orderservice.adapter.out.catalog;

import static org.junit.jupiter.api.Assertions.*;

import com.locpham.bookstore.orderservice.domain.exception.BookNotFoundException;
import com.locpham.bookstore.orderservice.domain.exception.CatalogUnavailableException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

class CatalogWebClientAdapterTest {

    private CatalogWebClientAdapter adapter;
    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        adapter =
                new CatalogWebClientAdapter(WebClient.builder(), mockWebServer.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void loadBook_success() {
        mockWebServer.enqueue(
                new MockResponse()
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody(
                                """
                        {"isbn":"1234567890","title":"Book","author":"Author","price":9.99}
                        """));

        var book = adapter.loadBook("1234567890").block();
        assertEquals("1234567890", book.isbn());
        assertEquals("Book", book.title());
        assertEquals(9.99, book.price());
    }

    @Test
    void loadBook_404_throwsBookNotFoundException() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        var ex =
                assertThrows(
                        BookNotFoundException.class, () -> adapter.loadBook("1234567890").block());
        assertTrue(ex.getMessage().contains("1234567890"));
    }

    @Test
    void loadBook_500_throwsCatalogUnavailableException() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        var ex =
                assertThrows(
                        CatalogUnavailableException.class,
                        () -> adapter.loadBook("1234567890").block());
        assertTrue(ex.getMessage().contains("1234567890"));
    }

    @Test
    void loadBook_503_throwsCatalogUnavailableException() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));

        assertThrows(
                CatalogUnavailableException.class, () -> adapter.loadBook("1234567890").block());
    }

    @Test
    void loadBook_connectionReset_throwsCatalogUnavailableException() {
        mockWebServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

        assertThrows(
                CatalogUnavailableException.class, () -> adapter.loadBook("1234567890").block());
    }

    @Test
    void loadBook_timeout_throwsCatalogUnavailableException() {
        mockWebServer.enqueue(new MockResponse().setBodyDelay(30, TimeUnit.SECONDS).setBody("{}"));

        assertThrows(
                CatalogUnavailableException.class, () -> adapter.loadBook("1234567890").block());
    }
}
