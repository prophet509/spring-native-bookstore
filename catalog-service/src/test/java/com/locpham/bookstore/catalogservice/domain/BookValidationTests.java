package com.locpham.bookstore.catalogservice.domain;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class BookValidationTests {
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void whenAllFieldsCorrectThenValidationPasses() {
        var book = Book.build("1234567890", "Title", "Author", 9.90, "Polarsophia");
        var violations = validator.validate(book);
        assertTrue(violations.isEmpty());
    }

    @Test
    void whenIsbnDefinedButInvalidThenValidationFails() {
        var book = Book.build("a234567890", "Title", "Author", 9.90, "Polarsophia");
        var violations = validator.validate(book);
        assertTrue(violations.size() == 1);
        assertThat(violations.iterator().next().getMessage())
                .isEqualTo("The ISBN format must follow the standards ISBN-10 or ISBN-13.");
    }
}
