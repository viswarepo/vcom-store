package com.shop.inventory.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {
  public enum Status { RESERVED, COMMITTED, RELEASED }

  @Id
  private UUID orderId;
  @Enumerated(EnumType.STRING)
  private Status status = Status.RESERVED;
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "reservation_items", joinColumns = @JoinColumn(name = "order_id"))
  private List<ReservedItem> items = new ArrayList<>();
  private Instant createdAt = Instant.now();

  protected Reservation() {}
  public Reservation(UUID orderId, List<ReservedItem> items) { this.orderId = orderId; this.items = items; }

  public UUID getOrderId() { return orderId; }
  public Status getStatus() { return status; }
  public void setStatus(Status status) { this.status = status; }
  public List<ReservedItem> getItems() { return items; }
}
