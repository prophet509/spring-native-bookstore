package com.locpham.bookstore.catalogservice.web;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.locpham.bookstore.catalogservice.domain.BookNotFoundException;
import com.locpham.bookstore.catalogservice.domain.BookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
public class BookControllerMvcTests {

    @Autowired private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean private BookService bookService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void whenGetBookNotFoundThenShouldReturn404() throws Exception {
        String isbn = "1234567890";
        given(bookService.viewBookDetail(isbn)).willThrow(new BookNotFoundException(isbn));
        mockMvc.perform(get("/books/" + isbn)).andExpect(status().isNotFound());
    }
}
