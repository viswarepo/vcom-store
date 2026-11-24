package com.shop.order.config;

import com.shop.order.event.Events;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
  @Bean NewTopic orderCreated() { return TopicBuilder.name(Events.ORDER_CREATED).partitions(3).replicas(1).build(); }
  @Bean NewTopic orderFulfillment() { return TopicBuilder.name(Events.ORDER_FULFILLMENT).partitions(3).replicas(1).build(); }
  @Bean NewTopic orderConfirmed() { return TopicBuilder.name(Events.ORDER_CONFIRMED).partitions(3).replicas(1).build(); }
}
