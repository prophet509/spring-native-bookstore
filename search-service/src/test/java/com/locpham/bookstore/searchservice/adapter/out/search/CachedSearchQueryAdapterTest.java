package com.locpham.bookstore.searchservice.adapter.out.search;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.locpham.bookstore.searchservice.domain.BookDocument;
import com.locpham.bookstore.searchservice.domain.SearchPage;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class CachedSearchQueryAdapterTest {

    @Mock private ElasticsearchSearchQueryAdapter delegate;

    private CachedSearchQueryAdapter adapter;

    private final Pageable pageable = PageRequest.of(0, 10);
    private final BookDocument book = new BookDocument("1234567890", "Title", "Author", 9.9, "Pub");
    private final SearchPage<BookDocument> page = SearchPage.of(List.of(book), 0, 10, 1L);

    @BeforeEach
    void setUp() {
        adapter = new CachedSearchQueryAdapter(delegate);
    }

    @Test
    void searchByTitleCachesResultAfterFirstCall() {
        given(delegate.searchByTitle("Title", pageable)).willReturn(Mono.just(page));

        StepVerifier.create(adapter.searchByTitle("Title", pageable))
                .expectNext(page)
                .verifyComplete();
        StepVerifier.create(adapter.searchByTitle("Title", pageable))
                .expectNext(page)
                .verifyComplete();

        // Delegate hit only once; second call served from cache.
        verify(delegate, times(1)).searchByTitle("Title", pageable);
    }

    @Test
    void searchByAuthorDelegatesAndCaches() {
        given(delegate.searchByAuthor("Author", pageable)).willReturn(Mono.just(page));

        StepVerifier.create(adapter.searchByAuthor("Author", pageable))
                .expectNext(page)
                .verifyComplete();
        adapter.searchByAuthor("Author", pageable).block();

        verify(delegate, times(1)).searchByAuthor("Author", pageable);
    }

    @Test
    void searchByPublisherDelegatesAndCaches() {
        given(delegate.searchByPublisher("Pub", pageable)).willReturn(Mono.just(page));

        adapter.searchByPublisher("Pub", pageable).block();
        adapter.searchByPublisher("Pub", pageable).block();

        verify(delegate, times(1)).searchByPublisher("Pub", pageable);
    }

    @Test
    void searchByIsbnDelegatesAndCaches() {
        given(delegate.searchByIsbn("1234567890", pageable)).willReturn(Mono.just(page));

        adapter.searchByIsbn("1234567890", pageable).block();
        adapter.searchByIsbn("1234567890", pageable).block();

        verify(delegate, times(1)).searchByIsbn("1234567890", pageable);
    }

    @Test
    void searchAllDelegatesAndCaches() {
        given(delegate.searchAll(pageable)).willReturn(Mono.just(page));

        adapter.searchAll(pageable).block();
        adapter.searchAll(pageable).block();

        verify(delegate, times(1)).searchAll(pageable);
    }

    @Test
    void suggestByTitleDelegates() {
        given(delegate.suggestByTitle("Ti")).willReturn(Flux.just("Title"));

        StepVerifier.create(adapter.suggestByTitle("Ti")).expectNext("Title").verifyComplete();
    }

    @Test
    void suggestByAuthorDelegates() {
        given(delegate.suggestByAuthor("Au")).willReturn(Flux.just("Author"));

        StepVerifier.create(adapter.suggestByAuthor("Au")).expectNext("Author").verifyComplete();
    }
}
