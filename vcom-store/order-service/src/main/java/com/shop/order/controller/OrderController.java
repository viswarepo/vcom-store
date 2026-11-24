package com.shop.order.controller;

import com.shop.order.service.OrderService.CreateOrderRequest;
import com.shop.order.service.OrderService.CustomerStat;
import com.shop.order.domain.FulfillmentStatus;
import com.shop.order.domain.Invoice;
import com.shop.order.domain.Order;
import com.shop.order.dto.InvoiceDto;
import com.shop.order.dto.OrderDto;
import com.shop.order.event.Events;
import com.shop.order.service.InvoiceService;
import com.shop.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

  public record FulfillmentRequest(@NotNull FulfillmentStatus status, @Size(max = 100) String courier,
                                   @Size(max = 100) String trackingNumber, @Size(max = 500) String trackingUrl,
                                   LocalDate estimatedDelivery, @Size(max = 200) String message) {}
  public record UpdateRequest(@NotBlank @Size(max = 200) String message) {}

  private final OrderService service;
  private final InvoiceService invoices;
  private final KafkaTemplate<String, Object> kafka;

  public OrderController(OrderService service, InvoiceService invoices, KafkaTemplate<String, Object> kafka) {
    this.service = service;
    this.invoices = invoices;
    this.kafka = kafka;
  }

  // ---------------- customer ----------------

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OrderDto place(@RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-Tenant-Id") UUID tenantId,
                        @RequestHeader(value = "X-User-Name", required = false) String name,
                        @RequestHeader(value = "X-User-Email", required = false) String email,
                        @RequestHeader(value = "X-User-Phone", required = false) String phone,
                        @Valid @RequestBody CreateOrderRequest req) {
    Order order = service.create(userId, tenantId, decode(name, "Customer"), decode(email, null), decode(phone, null), req);
    kafka.send(Events.ORDER_CREATED, order.getId().toString(), service.toEvent(order));
    return OrderDto.from(order, null);
  }

  @GetMapping
  public List<OrderDto> mine(@RequestHeader("X-User-Id") UUID userId) {
    return service.mine(userId).stream().map(o -> OrderDto.from(o, null)).toList();
  }

  // ---------------- merchant team (owner and store admins) ----------------

  @GetMapping("/merchant")
  public List<OrderDto> forMerchant(@RequestHeader("X-Tenant-Id") UUID tenantId, @RequestHeader("X-User-Role") String role) {
    requireTeam(role);
    return service.forMerchant(tenantId).stream().map(o -> OrderDto.from(o, tenantId)).toList();
  }

  @GetMapping("/merchant/customer-stats")
  public List<CustomerStat> customerStats(@RequestHeader("X-Tenant-Id") UUID tenantId, @RequestHeader("X-User-Role") String role) {
    requireTeam(role);
    return service.customerStats(tenantId);
  }

  @GetMapping("/merchant/invoices")
  public List<InvoiceDto.Row> invoiceList(@RequestHeader("X-Tenant-Id") UUID tenantId, @RequestHeader("X-User-Role") String role) {
    requireTeam(role);
    return invoices.rows(tenantId);
  }

  @PatchMapping("/{id}/fulfillment")
  public OrderDto fulfil(@PathVariable UUID id, @Valid @RequestBody FulfillmentRequest req,
                         @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestHeader("X-User-Role") String role) {
    requireTeam(role);
    Order o = service.updateFulfillment(id, tenantId, req.status(),
        new OrderService.ShipmentInfo(req.courier(), req.trackingNumber(), req.trackingUrl(), req.estimatedDelivery(), req.message()));
    kafka.send(Events.ORDER_FULFILLMENT, id.toString(),
        new Events.OrderFulfillment(id, o.getCustomerId(), o.getFulfillment().name(), o.getCourier(), o.getTrackingNumber()));
    return OrderDto.from(o, tenantId);
  }

  @PostMapping("/{id}/tracking-updates")
  public OrderDto trackingUpdate(@PathVariable UUID id, @Valid @RequestBody UpdateRequest req,
                                 @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestHeader("X-User-Role") String role) {
    requireTeam(role);
    return OrderDto.from(service.addTrackingUpdate(id, tenantId, req.message()), tenantId);
  }

  // ---------------- shared ----------------

  @GetMapping("/{id}/invoice")
  public InvoiceDto invoice(@PathVariable UUID id, @RequestHeader("X-User-Id") UUID userId,
                            @RequestHeader("X-Tenant-Id") UUID tenantId, @RequestHeader("X-User-Role") String role) {
    Order o = service.get(id);
    boolean buyer = o.getCustomerId().equals(userId);
    boolean team = o.getMerchantId() != null && o.getMerchantId().equals(tenantId) && !"CUSTOMER".equals(role);
    if (!buyer && !team && !"ADMIN".equals(role))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
    Invoice inv = invoices.forOrder(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "The invoice is available once the order is confirmed"));
    return InvoiceDto.of(inv, o);
  }

  @GetMapping("/{id}")
  public OrderDto get(@PathVariable UUID id, @RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
    Order o = service.get(id);
    if (!o.getCustomerId().equals(userId) && !"ADMIN".equals(role))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
    return OrderDto.from(o, null);
  }

  private void requireTeam(String role) {
    if (!"MERCHANT".equals(role) && !"STORE_ADMIN".equals(role) && !"ADMIN".equals(role))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant access required");
  }

  private static String decode(String v, String fallback) {
    if (v == null || v.isBlank() || "null".equals(v)) return fallback;
    return URLDecoder.decode(v, StandardCharsets.UTF_8);
  }
}
