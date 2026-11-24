package com.shop.payment.pay;

import com.shop.payment.producer.OutcomePublisher;
import com.shop.payment.service.BillingService;
import com.shop.payment.service.PaymentService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentExpiryJob {

  private final PaymentService service;
  private final BillingService billing;
  private final OutcomePublisher publisher;

  public PaymentExpiryJob(PaymentService service, BillingService billing, OutcomePublisher publisher) {
    this.service = service;
    this.billing = billing;
    this.publisher = publisher;
  }

  @Scheduled(fixedDelayString = "${app.payment.sweep-ms:60000}")
  public void sweep() {
    service.expireStale().forEach(publisher::publish);   // unpaid online orders
    billing.expireSubscriptions();                       // plans whose paid period ended
  }
}
