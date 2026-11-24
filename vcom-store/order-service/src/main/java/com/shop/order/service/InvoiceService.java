package com.shop.order.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.shop.order.repository.InvoiceCounterRepository;
import com.shop.order.repository.InvoiceRepository;
import com.shop.order.repository.OrderRepository;
import com.shop.order.domain.Invoice;
import com.shop.order.domain.InvoiceCounter;
import com.shop.order.domain.Order;
import com.shop.order.dto.InvoiceDto;
import com.shop.order.dto.StoreClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

  private final InvoiceRepository invoices;
  private final InvoiceCounterRepository counters;
  private final OrderRepository orders;
  private final StoreClient stores;

  public InvoiceService(InvoiceRepository invoices, InvoiceCounterRepository counters,
                        OrderRepository orders, StoreClient stores) {
    this.invoices = invoices;
    this.counters = counters;
    this.orders = orders;
    this.stores = stores;
  }

  /** Issues the invoice for a paid order. Calling it again returns the existing one. */
  @Transactional
  public Invoice issue(Order order) {
    return invoices.findByOrderId(order.getId()).orElseGet(() -> {
      UUID merchantId = order.getMerchantId();
      StoreClient.StoreDto store = null;
      try { store = stores.details(merchantId); } catch (Exception ignored) { /* invoice still issues */ }

      InvoiceCounter counter = counters.lockFor(merchantId)
          .orElseGet(() -> counters.saveAndFlush(new InvoiceCounter(merchantId)));
      counter.setLastNumber(counter.getLastNumber() + 1);

      Invoice inv = new Invoice();
      inv.setOrderId(order.getId());
      inv.setMerchantId(merchantId);
      inv.setNumber(String.format("INV-%06d", counter.getLastNumber()));
      inv.setSellerName(store != null && store.name() != null ? store.name() : "Store");
      inv.setSellerAddress(store != null ? store.address() : null);
      inv.setSellerGstin(store != null ? store.gstin() : null);
      return invoices.save(inv);
    });
  }

  @Transactional(readOnly = true)
  public Optional<Invoice> forOrder(UUID orderId) { return invoices.findByOrderId(orderId); }

  @Transactional(readOnly = true)
  public List<InvoiceDto.Row> rows(UUID merchantId) {
    List<Invoice> list = invoices.findByMerchantIdOrderByIssuedAtDesc(merchantId);
    Map<UUID, Order> byId = orders.findAllById(list.stream().map(Invoice::getOrderId).toList()).stream()
        .collect(Collectors.toMap(Order::getId, Function.identity()));
    return list.stream().filter(i -> byId.containsKey(i.getOrderId()))
        .map(i -> {
          Order o = byId.get(i.getOrderId());
          return new InvoiceDto.Row(o.getId(), i.getNumber(), i.getIssuedAt(), o.getCustomerName(), o.getTotal());
        }).toList();
  }
}
