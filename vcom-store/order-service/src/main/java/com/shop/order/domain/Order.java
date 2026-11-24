package com.shop.order.domain;

import com.shop.order.event.OrderEvent;
import jakarta.persistence.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(nullable = false)
  private UUID customerId;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrderStatus status = OrderStatus.PENDING;
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal total;
  private UUID merchantId;   // the store this order was placed with
  private String paymentMethod;
  @Column(length = 1000)
  private String shippingAddress;
  private String failureReason;
  private String customerName;     // snapshot, so the merchant can see who bought
  private String customerEmail;
  private String customerPhone;
  @Enumerated(EnumType.STRING)
  private FulfillmentStatus fulfillment = FulfillmentStatus.NEW;
  private String trackingNote;
  private Instant createdAt = Instant.now();
  @Fetch(FetchMode.SUBSELECT)   // two eager lists would otherwise fail with MultipleBagFetchException
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
  private List<OrderItem> items = new ArrayList<>();
  @Fetch(FetchMode.SUBSELECT)
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "order_events", joinColumns = @JoinColumn(name = "order_id"))
  private List<OrderEvent> history = new ArrayList<>();
  private String courier;
  private String trackingNumber;
  @Column(length = 500)
  private String trackingUrl;
  private LocalDate estimatedDelivery;

  public UUID getId() { return id; }
  public UUID getCustomerId() { return customerId; }
  public void setCustomerId(UUID customerId) { this.customerId = customerId; }
  public UUID getMerchantId() { return merchantId; }
  public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
  public OrderStatus getStatus() { return status; }
  public void setStatus(OrderStatus status) { this.status = status; }
  public BigDecimal getTotal() { return total; }
  public void setTotal(BigDecimal total) { this.total = total; }
  public String getPaymentMethod() { return paymentMethod; }
  public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
  public String getShippingAddress() { return shippingAddress; }
  public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
  public String getFailureReason() { return failureReason; }
  public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
  public String getCustomerName() { return customerName; }
  public void setCustomerName(String customerName) { this.customerName = customerName; }
  public String getCustomerEmail() { return customerEmail; }
  public String getCustomerPhone() { return customerPhone; }
  public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
  public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
  public FulfillmentStatus getFulfillment() { return fulfillment == null ? FulfillmentStatus.NEW : fulfillment; }
  public void setFulfillment(FulfillmentStatus fulfillment) { this.fulfillment = fulfillment; }
  public String getTrackingNote() { return trackingNote; }
  public void setTrackingNote(String trackingNote) { this.trackingNote = trackingNote; }
  public List<OrderEvent> getHistory() { return history; }
  public String getCourier() { return courier; }
  public void setCourier(String courier) { this.courier = courier; }
  public String getTrackingNumber() { return trackingNumber; }
  public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
  public String getTrackingUrl() { return trackingUrl; }
  public void setTrackingUrl(String trackingUrl) { this.trackingUrl = trackingUrl; }
  public LocalDate getEstimatedDelivery() { return estimatedDelivery; }
  public void setEstimatedDelivery(LocalDate estimatedDelivery) { this.estimatedDelivery = estimatedDelivery; }
  public Instant getCreatedAt() { return createdAt; }
  public List<OrderItem> getItems() { return items; }
}
