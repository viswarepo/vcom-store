package com.shop.product.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
  public static final String PRODUCT_UPDATED = "product.updated";

  @Bean
  NewTopic productUpdatedTopic() {
    return TopicBuilder.name(PRODUCT_UPDATED).partitions(3).replicas(1).build();
  }
}
