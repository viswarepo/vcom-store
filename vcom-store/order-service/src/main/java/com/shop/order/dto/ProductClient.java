package com.shop.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/** Prices always come from product-service, never from the browser. */
@Component
public class ProductClient {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ProductDto(UUID id, String name, BigDecimal price, UUID merchantId) {}

  @Configuration
  static class Config {
    @Bean @LoadBalanced RestClient.Builder lbRestClientBuilder() { return RestClient.builder(); }
    @Bean RestClient productRestClient(RestClient.Builder b) { return b.baseUrl("http://product-service").build(); }
  }

  private final RestClient rest;

  public ProductClient(RestClient productRestClient) { this.rest = productRestClient; }

  public ProductDto get(UUID id) {
    try {
      return rest.get().uri("/api/products/{id}", id).retrieve().body(ProductDto.class);
    } catch (HttpClientErrorException.NotFound e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product in your cart no longer exists");
    }
  }
}
