package com.shop.order.service;

import com.shop.order.domain.FulfillmentStatus;
import com.shop.order.domain.OrderItem;
import com.shop.order.domain.OrderStatus;
import com.shop.order.event.Events.*;
import com.shop.order.domain.Order;
import com.shop.order.dto.ProductClient;
import com.shop.order.dto.StoreClient;
import com.shop.order.event.OrderEvent;
import com.shop.order.repository.OrderRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

  public record Line(@NotNull UUID productId, @Min(1) @Max(99) int quantity) {}
  public record CreateOrderRequest(@NotEmpty @Valid List<Line> items, @NotBlank String shippingAddress, @NotBlank String paymentMethod) {}

  private static final Set<String> PAYMENT_METHODS = Set.of("ONLINE", "CARD", "COD", "TEST_DECLINE");

  private final OrderRepository orders;
  private final ProductClient products;

  private final StoreClient stores;

  private final InvoiceService invoices;

  public OrderService(OrderRepository orders, ProductClient products, StoreClient stores, InvoiceService invoices) {
    this.orders = orders;
    this.products = products;
    this.stores = stores;
    this.invoices = invoices;
  }

  /** Step 1 of the saga: save the order as PENDING. The caller publishes order.created. */
  @Transactional
  public Order create(UUID customerId, UUID tenantId, String customerName, String customerEmail, String customerPhone, CreateOrderRequest req) {
    if (!PAYMENT_METHODS.contains(req.paymentMethod()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment method");

    if (!stores.isActive(tenantId))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This store is not open yet");

    Map<UUID, Integer> qty = new LinkedHashMap<>();
    req.items().forEach(l -> qty.merge(l.productId(), l.quantity(), Integer::sum));

    Order order = new Order();
    order.setCustomerId(customerId);
    order.setMerchantId(tenantId);
    order.setCustomerName(customerName);
    order.setCustomerEmail(customerEmail);
    order.setCustomerPhone(customerPhone);
    order.getHistory().add(new OrderEvent("PLACED", "Order placed"));
    order.setShippingAddress(req.shippingAddress());
    order.setPaymentMethod(req.paymentMethod());
    BigDecimal total = BigDecimal.ZERO;
    for (var e : qty.entrySet()) {
      var p = products.get(e.getKey());
      // A customer can only buy from the store they belong to
      if (!tenantId.equals(p.merchantId()))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "\"" + p.name() + "\" is not sold by this store");
      if (customerId.equals(p.merchantId()))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You can't buy your own product: " + p.name());
      order.getItems().add(new OrderItem(p.id(), p.name(), p.price(), e.getValue(), p.merchantId()));
      total = total.add(p.price().multiply(BigDecimal.valueOf(e.getValue())));
    }
    order.setTotal(total);
    return orders.save(order);
  }

  public OrderCreated toEvent(Order o) {
    return new OrderCreated(o.getId(), o.getCustomerId(), o.getMerchantId(),
        o.getItems().stream().map(i -> new OrderCreated.Item(i.getProductId(), i.getQuantity(), i.getPrice())).toList(),
        o.getTotal(), o.getPaymentMethod());
  }

  @Transactional
  public void markReserved(UUID orderId) {
    orders.findById(orderId).filter(o -> o.getStatus() == OrderStatus.PENDING)
        .ifPresent(o -> o.setStatus(OrderStatus.STOCK_RESERVED));
  }

  /** Returns the confirmed event, or null when the order is unknown or already final (duplicate delivery). */
  @Transactional
  public OrderConfirmed confirm(UUID orderId) {
    return orders.findById(orderId).filter(o -> !o.getStatus().isFinal()).map(o -> {
      o.setStatus(OrderStatus.CONFIRMED);
      invoices.issue(o);
      o.getHistory().add(new OrderEvent("CONFIRMED", "Payment received. Order confirmed."));
      return new OrderConfirmed(o.getId(), o.getCustomerId(), o.getTotal());
    }).orElse(null);
  }

  @Transactional
  public void cancel(UUID orderId, String reason) {
    orders.findById(orderId).filter(o -> !o.getStatus().isFinal()).ifPresent(o -> {
      o.setStatus(OrderStatus.CANCELLED);
      o.setFailureReason(reason);
      o.getHistory().add(new OrderEvent("CANCELLED", reason == null ? "Order cancelled" : reason));
    });
  }

  @Transactional(readOnly = true)
  public List<Order> mine(UUID customerId) { return orders.findByCustomerIdOrderByCreatedAtDesc(customerId); }

  @Transactional(readOnly = true)
  public List<Order> forMerchant(UUID merchantId) { return orders.findByMerchantIdOrderByCreatedAtDesc(merchantId); }

  public record ShipmentInfo(String courier, String trackingNumber, String trackingUrl,
                             java.time.LocalDate estimatedDelivery, String message) {}

  /** Merchant team moves a paid order forward: PACKED, SHIPPED, DELIVERED. */
  @Transactional
  public Order updateFulfillment(UUID orderId, UUID tenantId, FulfillmentStatus next, ShipmentInfo info) {
    Order o = ownedPaidOrder(orderId, tenantId);
    if (next.ordinal() <= o.getFulfillment().ordinal())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An order can only move forward");
    if (info != null) {
      // Only http(s) links, so a tracking link can never be a script URL
      if (!blank(info.trackingUrl()) && !info.trackingUrl().trim().matches("(?i)^https?://\\S+$"))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The tracking link must start with http:// or https://");
      if (!blank(info.courier())) o.setCourier(info.courier().trim());
      if (!blank(info.trackingNumber())) o.setTrackingNumber(info.trackingNumber().trim());
      if (!blank(info.trackingUrl())) o.setTrackingUrl(info.trackingUrl().trim());
      if (info.estimatedDelivery() != null) o.setEstimatedDelivery(info.estimatedDelivery());
    }
    o.setFulfillment(next);
    o.getHistory().add(new OrderEvent(next.name(), eventMessage(next, o, info)));
    return o;
  }

  /** An in-transit note such as "Out for delivery" or "Reached Chennai hub". */
  @Transactional
  public Order addTrackingUpdate(UUID orderId, UUID tenantId, String message) {
    Order o = ownedPaidOrder(orderId, tenantId);
    if (o.getFulfillment() != FulfillmentStatus.SHIPPED)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Updates can be added while the order is in transit");
    o.getHistory().add(new OrderEvent("UPDATE", message.trim()));
    return o;
  }

  private Order ownedPaidOrder(UUID orderId, UUID tenantId) {
    Order o = orders.findById(orderId)
        .filter(x -> tenantId.equals(x.getMerchantId()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    if (o.getStatus() != OrderStatus.CONFIRMED)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only paid orders can be shipped");
    return o;
  }

  private static boolean blank(String s) { return s == null || s.isBlank(); }

  private static String eventMessage(FulfillmentStatus s, Order o, ShipmentInfo info) {
    if (info != null && !blank(info.message())) return info.message().trim();
    return switch (s) {
      case PACKED -> "Packed and ready for pickup";
      case SHIPPED -> blank(o.getCourier()) ? "Shipped" : "Shipped with " + o.getCourier();
      case DELIVERED -> "Delivered";
      default -> s.name();
    };
  }

  public record CustomerStat(UUID customerId, long orders, BigDecimal spent, java.time.Instant lastOrderAt) {}

  @Transactional(readOnly = true)
  public List<CustomerStat> customerStats(UUID merchantId) {
    return orders.findByMerchantIdOrderByCreatedAtDesc(merchantId).stream()
        .collect(java.util.stream.Collectors.groupingBy(Order::getCustomerId)).entrySet().stream()
        .map(e -> {
          var paid = e.getValue().stream().filter(o -> o.getStatus() == OrderStatus.CONFIRMED).toList();
          BigDecimal spent = paid.stream().map(Order::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
          var last = e.getValue().stream().map(Order::getCreatedAt).max(Comparator.naturalOrder()).orElse(null);
          return new CustomerStat(e.getKey(), paid.size(), spent, last);
        }).toList();
  }

  @Transactional(readOnly = true)
  public Order get(UUID id) {
    return orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
  }
}
