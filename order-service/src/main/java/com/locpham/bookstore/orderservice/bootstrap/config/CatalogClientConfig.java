package com.locpham.bookstore.orderservice.bootstrap.config;

import com.locpham.bookstore.orderservice.adapter.out.catalog.CatalogWebClientAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class CatalogClientConfig {

    @Bean
    public CatalogWebClientAdapter catalogWebClientAdapter(
            WebClient.Builder webClientBuilder,
            @Value("${polar.catalog-service-url}") String catalogServiceUrl) {
        return new CatalogWebClientAdapter(webClientBuilder, catalogServiceUrl);
    }
}
