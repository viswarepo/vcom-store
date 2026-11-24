package com.shop.order.dto;

import com.shop.order.domain.Invoice;
import com.shop.order.domain.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceDto(UUID orderId, String number, Instant issuedAt, Party seller, Party buyer,
                         List<OrderDto.Line> lines, BigDecimal total, String paymentMethod, String paymentNote) {

  public record Party(String name, String address, String gstin, String email) {}
  public record Row(UUID orderId, String number, Instant issuedAt, String customerName, BigDecimal total) {}

  public static InvoiceDto of(Invoice inv, Order o) {
    var lines = o.getItems().stream()
        .map(i -> new OrderDto.Line(i.getProductId(), i.getName(), i.getPrice(), i.getQuantity())).toList();
    String note = "COD".equals(o.getPaymentMethod()) ? "Cash on delivery" : "Paid online";
    return new InvoiceDto(o.getId(), inv.getNumber(), inv.getIssuedAt(),
        new Party(inv.getSellerName(), inv.getSellerAddress(), inv.getSellerGstin(), null),
        new Party(o.getCustomerName(), o.getShippingAddress(), null, o.getCustomerEmail()),
        lines, o.getTotal(), o.getPaymentMethod(), note);
  }
}
