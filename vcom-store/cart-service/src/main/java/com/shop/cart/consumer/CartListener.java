package com.shop.cart.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

import com.shop.cart.service.CartService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** An order was confirmed, so the customer's cart is emptied. */
@Component
public class CartListener {

  record OrderConfirmed(UUID orderId, UUID customerId) {}

  private final ObjectMapper mapper;
  private final CartService cart;

  public CartListener(ObjectMapper mapper, CartService cart) {
    this.mapper = mapper;
    this.cart = cart;
  }

  @KafkaListener(topics = "order.confirmed")
  void onOrderConfirmed(String payload) throws Exception {
    cart.clear(mapper.readValue(payload, OrderConfirmed.class).customerId());
  }
}
