package com.shop.order.dto;

import com.shop.order.domain.FulfillmentStatus;
import com.shop.order.domain.Order;
import com.shop.order.domain.OrderStatus;
import com.shop.order.event.OrderEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record OrderDto(UUID id, UUID customerId, String customerName, String customerEmail, String customerPhone, OrderStatus status,
                       FulfillmentStatus fulfillment, String trackingNote, BigDecimal total, String paymentMethod,
                       String shippingAddress, String failureReason, Instant createdAt, List<Line> items,
                       String courier, String trackingNumber, String trackingUrl, LocalDate estimatedDelivery,
                       List<Event> history) {

  public record Line(UUID productId, String name, BigDecimal price, int quantity) {}
  public record Event(Instant at, String status, String message) {}

  /** merchantId != null restricts the view to that merchant's own lines (and their subtotal). */
  public static OrderDto from(Order o, UUID merchantId) {
    var lines = o.getItems().stream()
        .filter(i -> merchantId == null || merchantId.equals(i.getMerchantId()))
        .map(i -> new Line(i.getProductId(), i.getName(), i.getPrice(), i.getQuantity()))
        .toList();
    BigDecimal total = merchantId == null ? o.getTotal()
        : lines.stream().map(l -> l.price().multiply(BigDecimal.valueOf(l.quantity()))).reduce(BigDecimal.ZERO, BigDecimal::add);
    var history = o.getHistory().stream()
        .sorted(Comparator.comparing(OrderEvent::getOccurredAt))
        .map(e -> new Event(e.getOccurredAt(), e.getStatus(), e.getMessage()))
        .toList();
    return new OrderDto(o.getId(), o.getCustomerId(), o.getCustomerName(), o.getCustomerEmail(), o.getCustomerPhone(), o.getStatus(),
        o.getFulfillment(), o.getTrackingNote(), total, o.getPaymentMethod(), o.getShippingAddress(),
        o.getFailureReason(), o.getCreatedAt(), lines, o.getCourier(), o.getTrackingNumber(), o.getTrackingUrl(),
        o.getEstimatedDelivery(), history);
  }
}
