package com.locpham.bookstore.searchservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchPageTests {

    @Test
    void firstPageOfMany() {
        var page = SearchPage.of(List.of("a", "b"), 0, 2, 5);

        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.hasPrevious()).isFalse();
    }

    @Test
    void middlePage() {
        var page = SearchPage.of(List.of("c", "d"), 1, 2, 5);

        assertThat(page.hasNext()).isTrue();
        assertThat(page.hasPrevious()).isTrue();
    }

    @Test
    void lastPagePartial() {
        var page = SearchPage.of(List.of("e"), 2, 2, 5);

        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isTrue();
    }

    @Test
    void emptyResult() {
        var page = SearchPage.<String>of(List.of(), 0, 10, 0);

        assertThat(page.totalPages()).isZero();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.hasPrevious()).isFalse();
    }
}
