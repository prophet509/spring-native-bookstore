package com.locpham.bookstore.searchservice.application.service;

import static org.mockito.BDDMockito.given;

import com.locpham.bookstore.searchservice.application.out.search.SearchQuery;
import com.locpham.bookstore.searchservice.domain.BookDocument;
import com.locpham.bookstore.searchservice.domain.SearchPage;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SearchBookServiceTests {

    @Mock private SearchQuery searchQuery;

    @InjectMocks private SearchBookService service;

    private final BookDocument book =
            new BookDocument("1234567890", "Spring Boot", "Author", 9.9, "Pub");

    @Test
    void searchByTitleDelegatesToPort() {
        var pageable = PageRequest.of(0, 10);
        var page = SearchPage.of(List.of(book), 0, 10, 1L);
        given(searchQuery.searchByTitle("Spring", pageable)).willReturn(Mono.just(page));

        StepVerifier.create(service.search("Spring", pageable)).expectNext(page).verifyComplete();
    }

    @Test
    void searchByAuthorDelegatesToPort() {
        var pageable = PageRequest.of(0, 10);
        var page = SearchPage.of(List.of(book), 0, 10, 1L);
        given(searchQuery.searchByAuthor("Author", pageable)).willReturn(Mono.just(page));

        StepVerifier.create(service.searchByAuthor("Author", pageable))
                .expectNext(page)
                .verifyComplete();
    }

    @Test
    void searchAllDelegatesToPort() {
        var pageable = PageRequest.of(0, 10);
        var page = SearchPage.of(List.of(book), 0, 10, 1L);
        given(searchQuery.searchAll(pageable)).willReturn(Mono.just(page));

        StepVerifier.create(service.searchAll(pageable)).expectNext(page).verifyComplete();
    }

    @Test
    void suggestByTitleDelegatesToPort() {
        given(searchQuery.suggestByTitle("Spr")).willReturn(Flux.just("Spring Boot"));

        StepVerifier.create(service.suggestByTitle("Spr"))
                .expectNext("Spring Boot")
                .verifyComplete();
    }

    @Test
    void searchByPublisherDelegatesToPort() {
        var pageable = PageRequest.of(0, 10);
        var page = SearchPage.of(List.of(book), 0, 10, 1L);
        given(searchQuery.searchByPublisher("Pub", pageable)).willReturn(Mono.just(page));

        StepVerifier.create(service.searchByPublisher("Pub", pageable))
                .expectNext(page)
                .verifyComplete();
    }

    @Test
    void searchByIsbnDelegatesToPort() {
        var pageable = PageRequest.of(0, 10);
        var page = SearchPage.of(List.of(book), 0, 10, 1L);
        given(searchQuery.searchByIsbn("1234567890", pageable)).willReturn(Mono.just(page));

        StepVerifier.create(service.searchByIsbn("1234567890", pageable))
                .expectNext(page)
                .verifyComplete();
    }

    @Test
    void suggestByAuthorDelegatesToPort() {
        given(searchQuery.suggestByAuthor("Aut")).willReturn(Flux.just("Author"));

        StepVerifier.create(service.suggestByAuthor("Aut")).expectNext("Author").verifyComplete();
    }

    @Test
    void searchByTitlePropagatesError() {
        var pageable = PageRequest.of(0, 10);
        given(searchQuery.searchByTitle("x", pageable))
                .willReturn(Mono.error(new RuntimeException("es down")));

        StepVerifier.create(service.search("x", pageable))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void searchByAuthorPropagatesError() {
        var pageable = PageRequest.of(0, 10);
        given(searchQuery.searchByAuthor("x", pageable))
                .willReturn(Mono.error(new RuntimeException("es down")));

        StepVerifier.create(service.searchByAuthor("x", pageable))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void searchByPublisherPropagatesError() {
        var pageable = PageRequest.of(0, 10);
        given(searchQuery.searchByPublisher("x", pageable))
                .willReturn(Mono.error(new RuntimeException("es down")));

        StepVerifier.create(service.searchByPublisher("x", pageable))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void searchByIsbnPropagatesError() {
        var pageable = PageRequest.of(0, 10);
        given(searchQuery.searchByIsbn("x", pageable))
                .willReturn(Mono.error(new RuntimeException("es down")));

        StepVerifier.create(service.searchByIsbn("x", pageable))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void searchAllPropagatesError() {
        var pageable = PageRequest.of(0, 10);
        given(searchQuery.searchAll(pageable))
                .willReturn(Mono.error(new RuntimeException("es down")));

        StepVerifier.create(service.searchAll(pageable))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void suggestByTitlePropagatesError() {
        given(searchQuery.suggestByTitle("x"))
                .willReturn(Flux.error(new RuntimeException("es down")));

        StepVerifier.create(service.suggestByTitle("x"))
                .expectError(RuntimeException.class)
                .verify();
    }

    @Test
    void suggestByAuthorPropagatesError() {
        given(searchQuery.suggestByAuthor("x"))
                .willReturn(Flux.error(new RuntimeException("es down")));

        StepVerifier.create(service.suggestByAuthor("x"))
                .expectError(RuntimeException.class)
                .verify();
    }
}
