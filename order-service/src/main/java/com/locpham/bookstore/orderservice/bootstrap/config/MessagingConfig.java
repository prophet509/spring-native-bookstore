package com.locpham.bookstore.orderservice.bootstrap.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class MessagingConfig {

    @Bean
    public NewTopic orderCreatedEventsTopic(
            @Value("${polar.kafka.topics.order-created-events:order-created-events}")
                    String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderAcceptedTopic(
            @Value("${polar.kafka.topics.order-accepted:order-accepted}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderDispatchedEventsTopic(
            @Value("${polar.kafka.topics.order-dispatched-events:order-dispatched-events}")
                    String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryEventsTopic(
            @Value("${polar.kafka.topics.inventory-events:inventory-events}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }
}
