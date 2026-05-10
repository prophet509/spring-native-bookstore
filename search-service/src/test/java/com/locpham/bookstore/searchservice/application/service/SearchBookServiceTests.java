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
}
