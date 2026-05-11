package com.locpham.bookstore.edgeservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.config.enabled=false", "spring.session.store-type=none"})
@Import(TestChannelBinderConfiguration.class)
class EdgeServiceApplicationTests {

    @Test
    void verifyThatSpringContextLoads() {}
}
