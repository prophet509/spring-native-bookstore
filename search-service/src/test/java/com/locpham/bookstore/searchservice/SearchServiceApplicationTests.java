package com.locpham.bookstore.searchservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
            "spring.cloud.config.enabled=false",
            "spring.cloud.config.fail-fast=false",
            "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test",
            "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration,org.springframework.boot.autoconfigure.data.elasticsearch.ReactiveElasticsearchRepositoriesAutoConfiguration"
        })
class SearchServiceApplicationTests {

    @Test
    void contextLoads() {}
}
