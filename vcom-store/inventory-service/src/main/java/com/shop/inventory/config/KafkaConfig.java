package com.shop.inventory.config;

import com.shop.inventory.event.Events;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
  @Bean NewTopic orderCreated() { return TopicBuilder.name(Events.ORDER_CREATED).partitions(3).replicas(1).build(); }
  @Bean NewTopic inventoryReserved() { return TopicBuilder.name(Events.INVENTORY_RESERVED).partitions(3).replicas(1).build(); }
  @Bean NewTopic inventoryFailed() { return TopicBuilder.name(Events.INVENTORY_FAILED).partitions(3).replicas(1).build(); }
  @Bean NewTopic paymentCompleted() { return TopicBuilder.name(Events.PAYMENT_COMPLETED).partitions(3).replicas(1).build(); }
  @Bean NewTopic paymentFailed() { return TopicBuilder.name(Events.PAYMENT_FAILED).partitions(3).replicas(1).build(); }
}
