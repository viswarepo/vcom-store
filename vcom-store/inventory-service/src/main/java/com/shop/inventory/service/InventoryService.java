package com.shop.inventory.service;

import com.shop.inventory.domain.Reservation;
import com.shop.inventory.event.Events.*;
import java.util.*;
import java.util.stream.Collectors;

import com.shop.inventory.domain.ReservedItem;
import com.shop.inventory.domain.Stock;
import com.shop.inventory.event.Events;
import com.shop.inventory.repository.ReservationRepository;
import com.shop.inventory.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

  private final StockRepository stock;
  private final ReservationRepository reservations;

  public InventoryService(StockRepository stock, ReservationRepository reservations) {
    this.stock = stock;
    this.reservations = reservations;
  }

  /** All-or-nothing reservation. Returns null if this order was already processed (duplicate delivery). */
  @Transactional
  public Events.Outcome reserve(Events.OrderCreated order) {
    if (reservations.existsById(order.orderId())) return null;

    Map<UUID, Integer> need = new TreeMap<>();
    for (var item : order.items()) need.merge(item.productId(), item.quantity(), Integer::sum);

    Map<UUID, Stock> rows = stock.lockAll(need.keySet()).stream()
        .collect(Collectors.toMap(Stock::getProductId, s -> s));

    for (var e : need.entrySet()) {
      Stock s = rows.get(e.getKey());
      if (s == null || s.getQuantity() < e.getValue()) {
        return new Events.InventoryFailed(order.orderId(), "Not enough stock for product " + e.getKey());
      }
    }
    List<ReservedItem> reserved = new ArrayList<>();
    for (var e : need.entrySet()) {
      Stock s = rows.get(e.getKey());
      s.setQuantity(s.getQuantity() - e.getValue());
      reserved.add(new ReservedItem(e.getKey(), e.getValue()));
    }
    reservations.save(new Reservation(order.orderId(), reserved));
    return new InventoryReserved(order.orderId(), order.customerId(), order.merchantId(), order.total(), order.paymentMethod());
  }

  /** Compensation: payment failed, so put the stock back. */
  @Transactional
  public void release(UUID orderId) {
    reservations.findById(orderId).filter(r -> r.getStatus() == Reservation.Status.RESERVED).ifPresent(r -> {
      Set<UUID> ids = r.getItems().stream().map(ReservedItem::getProductId).collect(Collectors.toCollection(TreeSet::new));
      Map<UUID, Stock> rows = stock.lockAll(ids).stream().collect(Collectors.toMap(Stock::getProductId, s -> s));
      for (ReservedItem i : r.getItems()) {
        Stock s = rows.get(i.getProductId());
        if (s != null) s.setQuantity(s.getQuantity() + i.getQuantity());
      }
      r.setStatus(Reservation.Status.RELEASED);
    });
  }

  /** Payment succeeded: the reservation becomes final. */
  @Transactional
  public void commit(UUID orderId) {
    reservations.findById(orderId).filter(r -> r.getStatus() == Reservation.Status.RESERVED)
        .ifPresent(r -> r.setStatus(Reservation.Status.COMMITTED));
  }

  @Transactional
  public void setStock(UUID productId, int quantity) {
    Stock s = stock.lockAll(Set.of(productId)).stream().findFirst().orElse(null);
    if (s == null) stock.save(new Stock(productId, quantity));
    else s.setQuantity(quantity);
  }

  @Transactional(readOnly = true)
  public Map<UUID, Integer> levels(Collection<UUID> ids) {
    Map<UUID, Integer> out = new LinkedHashMap<>();
    ids.forEach(id -> out.put(id, 0));
    stock.findAllById(ids).forEach(s -> out.put(s.getProductId(), s.getQuantity()));
    return out;
  }
}
