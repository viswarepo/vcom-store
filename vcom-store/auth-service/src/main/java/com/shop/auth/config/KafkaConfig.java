package com.shop.auth.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
  public static final String USER_REGISTERED = "user.registered";

  @Bean
  NewTopic userRegisteredTopic() {
    return TopicBuilder.name(USER_REGISTERED).partitions(3).replicas(1).build();
  }
}
