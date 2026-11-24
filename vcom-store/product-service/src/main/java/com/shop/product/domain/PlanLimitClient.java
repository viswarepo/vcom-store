package com.shop.product.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Asks payment-service how many products this merchant's plan allows (null = unlimited). */
@Component
public class PlanLimitClient {

  private static final Logger log = LoggerFactory.getLogger(PlanLimitClient.class);

  @JsonIgnoreProperties(ignoreUnknown = true)
  record Limits(Integer maxProducts) {}

  @Configuration
  static class Config {
    @Bean @LoadBalanced RestClient.Builder lbRestClientBuilder() { return RestClient.builder(); }
    @Bean RestClient paymentRestClient(RestClient.Builder b) { return b.baseUrl("http://payment-service").build(); }
  }

  private final RestClient rest;

  public PlanLimitClient(RestClient paymentRestClient) { this.rest = paymentRestClient; }

  /** If payment-service cannot be reached we allow the product rather than block the seller. */
  public Integer maxProducts(UUID merchantId) {
    try {
      Limits l = rest.get().uri("/api/payments/limits/{id}", merchantId).retrieve().body(Limits.class);
      return l == null ? null : l.maxProducts();
    } catch (RuntimeException e) {
      log.warn("Could not read plan limits for {}: {}", merchantId, e.getMessage());
      return null;
    }
  }
}
