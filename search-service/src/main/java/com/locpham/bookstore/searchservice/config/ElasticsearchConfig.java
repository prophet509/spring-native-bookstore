package com.locpham.bookstore.searchservice.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.elasticsearch.autoconfigure.ElasticsearchProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchConfiguration;
import org.springframework.data.elasticsearch.repository.config.EnableReactiveElasticsearchRepositories;

@Configuration(proxyBeanMethods = false)
@EnableReactiveElasticsearchRepositories(
        basePackages = "com.locpham.bookstore.searchservice.adapter.out")
class ElasticsearchConfig extends ReactiveElasticsearchConfiguration {

    private final ElasticsearchProperties elasticsearchProperties;

    ElasticsearchConfig(ElasticsearchProperties elasticsearchProperties) {
        this.elasticsearchProperties = elasticsearchProperties;
    }

    @Override
    public ClientConfiguration clientConfiguration() {
        List<String> endpoints =
                elasticsearchProperties.getUris().stream()
                        .map(URI::create)
                        .map(uri -> uri.getHost() + ":" + uri.getPort())
                        .toList();

        boolean sslEnabled =
                elasticsearchProperties.getUris().stream()
                        .map(URI::create)
                        .anyMatch(uri -> "https".equalsIgnoreCase(uri.getScheme()));
        ClientConfiguration.TerminalClientConfigurationBuilder builder;
        if (sslEnabled) {
            builder =
                    ClientConfiguration.builder()
                            .connectedTo(endpoints.toArray(String[]::new))
                            .usingSsl();
        } else {
            builder = ClientConfiguration.builder().connectedTo(endpoints.toArray(String[]::new));
        }

        if (elasticsearchProperties.getConnectionTimeout() != null) {
            builder = builder.withConnectTimeout(elasticsearchProperties.getConnectionTimeout());
        }
        if (elasticsearchProperties.getSocketTimeout() != null) {
            builder = builder.withSocketTimeout(elasticsearchProperties.getSocketTimeout());
        }

        return builder.build();
    }
}
