package com.shop.payment.config;

import com.shop.payment.event.Events;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {
  @Bean NewTopic paymentCompleted() { return TopicBuilder.name(Events.PAYMENT_COMPLETED).partitions(3).replicas(1).build(); }
  @Bean NewTopic merchantActivated() { return TopicBuilder.name(Events.MERCHANT_ACTIVATED).partitions(1).replicas(1).build(); }
  @Bean NewTopic merchantDeactivated() { return TopicBuilder.name(Events.MERCHANT_DEACTIVATED).partitions(1).replicas(1).build(); }
  @Bean NewTopic paymentFailed() { return TopicBuilder.name(Events.PAYMENT_FAILED).partitions(3).replicas(1).build(); }
}
