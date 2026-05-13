package com.locpham.bookstore.catalogservice.adapter.in;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.locpham.bookstore.catalogservice.application.port.in.AddBookUseCase;
import com.locpham.bookstore.catalogservice.application.port.in.EditBookUseCase;
import com.locpham.bookstore.catalogservice.application.port.in.ViewBookDetailUseCase;
import com.locpham.bookstore.catalogservice.application.port.in.ViewListBookUseCase;
import com.locpham.bookstore.catalogservice.domain.book.Book;
import com.locpham.bookstore.catalogservice.domain.book.exception.BookAlreadyExistsException;
import com.locpham.bookstore.catalogservice.domain.book.exception.BookNotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
public class BookControllerMvcTests {

    @Autowired private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @MockitoBean private ViewBookDetailUseCase viewBookDetailUseCase;
    @MockitoBean private ViewListBookUseCase viewListBookUseCase;
    @MockitoBean private AddBookUseCase addBookUseCase;
    @MockitoBean private EditBookUseCase editBookUseCase;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.webAppContextSetup(webApplicationContext)
                        .apply(springSecurity())
                        .build();
    }

    @Test
    void whenGetBookNotFoundThenShouldReturn404() throws Exception {
        String isbn = "1234567890";
        given(viewBookDetailUseCase.viewBookDetail(isbn))
                .willThrow(new BookNotFoundException(isbn));
        mockMvc.perform(get("/books/" + isbn)).andExpect(status().isNotFound());
    }

    @Test
    void whenCreateBookWithoutTokenThenShouldReturn401() throws Exception {
        var payload =
                """
                {"isbn":"1234567890","title":"Title","author":"Author","price":10.0,"publisher":"Polarsophia"}
                """;
        mockMvc.perform(post("/books").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void whenCreateBookWithCustomerRoleThenShouldReturn403() throws Exception {
        var payload =
                """
                {"isbn":"1234567890","title":"Title","author":"Author","price":10.0,"publisher":"Polarsophia"}
                """;
        mockMvc.perform(
                        post("/books")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_customer"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void whenCreateBookWithEmployeeRoleThenShouldReturn201() throws Exception {
        var book = Book.build("1234567890", "Title", "Author", 10.0, "Polarsophia");
        var payload =
                """
                {"isbn":"1234567890","title":"Title","author":"Author","price":10.0,"publisher":"Polarsophia"}
                """;
        given(addBookUseCase.addBookToCatalog(book)).willReturn(book);

        mockMvc.perform(
                        post("/books")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_employee"))))
                .andExpect(status().isCreated());
    }

    @Test
    void whenBookAlreadyExistThenShouldReturn409() throws Exception {
        var book = Book.build("1234567890", "Title", "Author", 10.0, "Polarsophia");

        given(addBookUseCase.addBookToCatalog(book))
                .willThrow(new BookAlreadyExistsException("1234567890"));

        var payload =
                """
                {"isbn":"1234567890","title":"Title","author":"Author","price":10.0,"publisher":"Polarsophia"}
                """;

        mockMvc.perform(
                        post("/books")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_employee"))))
                .andExpect(status().isConflict());
    }

    @Test
    void whenBookValidateErrorThenShouldReturn400() throws Exception {
        var payload =
                """
                    {"isbn":"12","title":"Title","author":"Author","price":10.0,"publisher":"Polarsophia"}
                """;

        mockMvc.perform(
                        post("/books")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                                .with(
                                        jwt().authorities(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_employee"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void whenGetBookListThenShouldReturn200() throws Exception {
        given(viewListBookUseCase.viewBookList())
                .willReturn(
                        List.of(
                                Book.build("1234567890", "Title", "Author", 10.0, "Polarsophia"),
                                Book.build("1234567891", "Title", "Author", 10.0, "Polarsophia")));
        mockMvc.perform(get("/books")).andExpectAll(status().isOk(), jsonPath("$", hasSize(2)));
    }
}
