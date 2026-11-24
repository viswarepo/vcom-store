package com.shop.order.domain;

public enum OrderStatus {
  PENDING,          // saved, waiting for stock
  STOCK_RESERVED,   // stock held, waiting for payment
  CONFIRMED,        // paid
  CANCELLED;        // out of stock or payment failed

  public boolean isFinal() { return this == CONFIRMED || this == CANCELLED; }
}
