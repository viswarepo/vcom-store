package com.shop.order.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Reads a merchant's store from store-service: is it open, and what to print on invoices. */
@Component
public class StoreClient {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record StoreDto(UUID ownerId, boolean active, String name, String address, String gstin) {}

  @Configuration
  static class Config {
    @Bean RestClient storeRestClient(RestClient.Builder b) { return b.clone().baseUrl("http://store-service").build(); }
  }

  private final RestClient rest;

  public StoreClient(RestClient storeRestClient) { this.rest = storeRestClient; }

  /** null when the store does not exist or the call fails. */
  public StoreDto details(UUID ownerId) {
    try {
      return rest.get().uri("/api/stores/owner/{id}", ownerId).retrieve().body(StoreDto.class);
    } catch (HttpClientErrorException.NotFound e) {
      return null;
    }
  }

  public boolean isActive(UUID ownerId) {
    StoreDto s = details(ownerId);
    return s != null && s.active();
  }
}
