package com.locpham.bookstore.searchservice;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka-native:latest"));
    }

    @Bean
    @ServiceConnection
    ElasticsearchContainer elasticsearchContainer() {
        return new ElasticsearchContainer(
                        DockerImageName.parse(
                                "docker.elastic.co/elasticsearch/elasticsearch:9.2.8"))
                .withEnv("disk.watermark.low", "98%")
                .withEnv("disk.watermark.high", "99%")
                .withEnv("disk.watermark.flood_stage", "99.5%");
    }
}
