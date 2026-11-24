package com.shop.payment.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import com.shop.payment.*;
import com.shop.payment.PaymentGateway;
import com.shop.payment.domain.*;
import com.shop.payment.event.Events;
import com.shop.payment.repository.PaymentRepository;
import com.shop.payment.repository.PlanRepository;
import com.shop.payment.RazorpayClient;
import com.shop.payment.dto.CheckoutDetails;
import com.shop.payment.repository.SettingsRepository;
import com.shop.payment.repository.SubscriptionPaymentRepository;
import com.shop.payment.repository.SubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Platform-owner billing: plans, merchant subscriptions, commission and earnings reports. */
@Service
public class BillingService {

  public record Summary(long orders, BigDecimal gross, BigDecimal commission, BigDecimal net, BigDecimal codCommissionDue) {}
  public record MerchantRow(UUID merchantId, Summary summary) {}
  public record AdminSummary(Summary total, BigDecimal subscriptionRevenue, List<MerchantRow> merchants) {}

  public record SubscriptionView(String status, boolean active, UUID planId, String planName, BigDecimal price,
                                 Plan.BillingPeriod billingPeriod, BigDecimal commissionPercent, Integer maxProducts,
                                 Instant currentPeriodEnd) {}
  /** checkout is null when the plan was activated at once (free plan, or the development gateway). */
  public record CheckoutResult(SubscriptionView subscription, CheckoutDetails checkout) {}
  public record Limits(Integer maxProducts, boolean subscribed) {}
  public record PlanData(String name, String description, BigDecimal price, Plan.BillingPeriod billingPeriod,
                         BigDecimal commissionPercent, Integer maxProducts, boolean active, int sortOrder) {}

  private final SettingsRepository settingsRepo;
  private final PlanRepository plans;
  private final SubscriptionRepository subscriptions;
  private final SubscriptionPaymentRepository subscriptionPayments;
  private final PaymentRepository payments;
  private final PaymentGateway gateway;
  private final KafkaTemplate<String, Object> kafka;
  private final RazorpayClient razorpay;
  private final int unsubscribedMaxProducts;

  public BillingService(SettingsRepository settingsRepo, PlanRepository plans, SubscriptionRepository subscriptions,
                        SubscriptionPaymentRepository subscriptionPayments, PaymentRepository payments,
                        PaymentGateway gateway, KafkaTemplate<String, Object> kafka, RazorpayClient razorpay,
                        @Value("${app.billing.unsubscribed-max-products:5}") int unsubscribedMaxProducts) {
    this.settingsRepo = settingsRepo;
    this.plans = plans;
    this.subscriptions = subscriptions;
    this.subscriptionPayments = subscriptionPayments;
    this.payments = payments;
    this.gateway = gateway;
    this.kafka = kafka;
    this.razorpay = razorpay;
    this.unsubscribedMaxProducts = unsubscribedMaxProducts;
  }

  // ---------------- default commission (used for merchants without an active plan) ----------------

  @Transactional
  public PlatformSettings settings() {
    return settingsRepo.findById(1L).orElseGet(() -> settingsRepo.save(new PlatformSettings()));
  }

  @Transactional
  public PlatformSettings updateSettings(BigDecimal commissionPercent) {
    if (commissionPercent.signum() < 0 || commissionPercent.compareTo(BigDecimal.valueOf(100)) > 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Commission must be between 0 and 100");
    PlatformSettings s = settings();
    s.setCommissionPercent(commissionPercent);
    return s;
  }

  /** The commission rate that applies to a sale by this merchant: their plan's, else the default. */
  @Transactional
  public BigDecimal commissionFor(UUID merchantId) {
    return subscriptions.findById(merchantId)
        .filter(s -> "ACTIVE".equals(s.getStatus()))
        .map(Subscription::getCommissionPercent)
        .orElseGet(() -> settings().getCommissionPercent());
  }

  /** What a merchant may do on their plan (read by product-service). */
  @Transactional(readOnly = true)
  public Limits limitsFor(UUID merchantId) {
    return subscriptions.findById(merchantId).filter(s -> "ACTIVE".equals(s.getStatus()))
        .map(s -> new Limits(s.getMaxProducts(), true))
        .orElse(new Limits(unsubscribedMaxProducts, false));
  }

  // ---------------- plans (platform owner) ----------------

  @Transactional(readOnly = true)
  public List<Plan> activePlans() { return plans.findByActiveTrueOrderBySortOrderAscPriceAsc(); }

  @Transactional(readOnly = true)
  public List<Plan> allPlans() { return plans.findAllByOrderBySortOrderAscPriceAsc(); }

  @Transactional
  public Plan savePlan(UUID id, PlanData d) {
    // Razorpay's smallest order is Rs 1, so a paid plan must cost at least that
    if (d.price().signum() > 0 && d.price().compareTo(BigDecimal.ONE) < 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A paid plan must cost at least Rs 1");
    Plan p = id == null ? new Plan() : plans.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));
    p.setName(d.name().trim());
    p.setDescription(d.description());
    p.setPrice(d.price());
    p.setBillingPeriod(d.billingPeriod());
    p.setCommissionPercent(d.commissionPercent());
    p.setMaxProducts(d.maxProducts());
    p.setActive(d.active());
    p.setSortOrder(d.sortOrder());
    return plans.save(p);
  }

  // ---------------- merchant subscription ----------------

  @Transactional(readOnly = true)
  public SubscriptionView subscription(UUID merchantId) { return view(merchantId); }

  /** Picks a plan. Free plans (and the development gateway) activate at once; otherwise returns Checkout details. */
  @Transactional
  public CheckoutResult startSubscription(UUID merchantId, UUID planId) {
    Plan plan = plans.findById(planId).filter(Plan::isActive)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "That plan is not available"));
    BigDecimal price = plan.getPrice();

    if (price.signum() <= 0 || !razorpay.enabled()) {
      String txn = "free";
      if (price.signum() > 0) {
        var result = gateway.charge(merchantId, price, "CARD");
        if (!result.success())
          throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Payment failed: " + result.failureReason());
        txn = result.transactionId();
      }
      SubscriptionPayment sp = subscriptionPayments.save(
          new SubscriptionPayment("local_" + UUID.randomUUID(), merchantId, plan.getId(), price));
      activate(sp, plan, txn);
      return new CheckoutResult(view(merchantId), null);
    }

    SubscriptionPayment sp = subscriptionPayments
        .findFirstByMerchantIdAndPlanIdAndStatusOrderByCreatedAtDesc(merchantId, planId, "CREATED")
        .filter(x -> x.getAmount().compareTo(price) == 0)
        .orElseGet(() -> {
          String orderId = razorpay.createOrder(PaymentService.toPaise(price),
              "sub_" + merchantId.toString().replace("-", ""),
              Map.of("merchantId", merchantId.toString(), "planId", planId.toString(), "purpose", "subscription"));
          return subscriptionPayments.save(new SubscriptionPayment(orderId, merchantId, planId, price));
        });
    return new CheckoutResult(view(merchantId),
        new CheckoutDetails("razorpay", razorpay.keyId(), sp.getRazorpayOrderId(), PaymentService.toPaise(price), "INR"));
  }

  /** The merchant's browser reports a successful payment. The order must be one we created for this merchant. */
  @Transactional
  public SubscriptionView confirmSubscription(UUID merchantId, String razorpayOrderId, String razorpayPaymentId, String signature) {
    SubscriptionPayment sp = subscriptionPayments.findById(razorpayOrderId == null ? "" : razorpayOrderId)
        .filter(x -> x.getMerchantId().equals(merchantId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No matching subscription payment"));
    if (!razorpay.verifyCheckoutSignature(sp.getRazorpayOrderId(), razorpayPaymentId, signature))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment verification failed");
    if ("CREATED".equals(sp.getStatus())) {
      Plan plan = plans.findById(sp.getPlanId()).orElseThrow();
      activate(sp, plan, razorpayPaymentId);
    }
    return view(merchantId);
  }

  /** Razorpay's signed webhook says this order was paid (does nothing for orders that are not subscriptions). */
  @Transactional
  public void confirmSubscriptionByOrder(String razorpayOrderId, String razorpayPaymentId) {
    subscriptionPayments.findById(razorpayOrderId).filter(x -> "CREATED".equals(x.getStatus()))
        .ifPresent(sp -> activate(sp, plans.findById(sp.getPlanId()).orElseThrow(), razorpayPaymentId));
  }

  private void activate(SubscriptionPayment sp, Plan plan, String paymentId) {
    sp.setStatus("PAID");
    sp.setPaidAt(Instant.now());
    sp.setRazorpayPaymentId(paymentId);

    Subscription sub = subscriptions.findById(sp.getMerchantId()).orElseGet(() -> new Subscription(sp.getMerchantId()));
    Instant start = Instant.now();
    // Renewing the same plan extends the period that is already paid for
    if (plan.getId().equals(sub.getPlanId()) && "ACTIVE".equals(sub.getStatus())
        && sub.getCurrentPeriodEnd() != null && sub.getCurrentPeriodEnd().isAfter(start)) {
      start = sub.getCurrentPeriodEnd();
    }
    Instant end = null;   // free plans never expire
    if (plan.getPrice().signum() > 0) {
      var at = start.atZone(ZoneOffset.UTC);
      end = (plan.getBillingPeriod() == Plan.BillingPeriod.YEARLY ? at.plusYears(1) : at.plusMonths(1)).toInstant();
    }
    sub.apply(plan, end);
    subscriptions.save(sub);
    kafka.send(Events.MERCHANT_ACTIVATED, sp.getMerchantId().toString(), new Events.MerchantActivated(sp.getMerchantId()));
  }

  /** Ends plans whose paid period is over and closes those stores until the merchant renews. */
  @Transactional
  public int expireSubscriptions() {
    List<Subscription> due = subscriptions.findByStatusAndCurrentPeriodEndBefore("ACTIVE", Instant.now());
    for (Subscription s : due) {
      s.setStatus("EXPIRED");
      kafka.send(Events.MERCHANT_DEACTIVATED, s.getMerchantId().toString(), new Events.MerchantDeactivated(s.getMerchantId()));
    }
    return due.size();
  }

  private SubscriptionView view(UUID merchantId) {
    return subscriptions.findById(merchantId)
        .map(s -> new SubscriptionView(s.getStatus(), "ACTIVE".equals(s.getStatus()), s.getPlanId(), s.getPlanName(),
            s.getPrice(), s.getBillingPeriod(), s.getCommissionPercent(), s.getMaxProducts(), s.getCurrentPeriodEnd()))
        .orElse(new SubscriptionView("NONE", false, null, null, null, null, null, null, null));
  }

  // ---------------- earnings reports ----------------

  @Transactional(readOnly = true)
  public Summary merchantSummary(UUID merchantId) {
    return summarize(payments.findByMerchantIdAndStatus(merchantId, Payment.Status.SUCCEEDED));
  }

  @Transactional(readOnly = true)
  public AdminSummary adminSummary() {
    List<Payment> all = payments.findByStatus(Payment.Status.SUCCEEDED).stream()
        .filter(p -> p.getMerchantId() != null).toList();
    List<MerchantRow> rows = all.stream().collect(Collectors.groupingBy(Payment::getMerchantId)).entrySet().stream()
        .map(e -> new MerchantRow(e.getKey(), summarize(e.getValue())))
        .sorted(Comparator.comparing((MerchantRow r) -> r.summary().gross()).reversed())
        .toList();
    BigDecimal subscriptionRevenue = subscriptionPayments.findAll().stream()
        .filter(sp -> "PAID".equals(sp.getStatus()))
        .map(SubscriptionPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    return new AdminSummary(summarize(all), subscriptionRevenue, rows);
  }

  private Summary summarize(List<Payment> list) {
    BigDecimal gross = BigDecimal.ZERO, commission = BigDecimal.ZERO, net = BigDecimal.ZERO, codDue = BigDecimal.ZERO;
    for (Payment p : list) {
      BigDecimal fee = p.getPlatformFee() == null ? BigDecimal.ZERO : p.getPlatformFee();
      BigDecimal merchantAmt = p.getMerchantAmount() == null ? p.getAmount() : p.getMerchantAmount();
      gross = gross.add(p.getAmount());
      commission = commission.add(fee);
      net = net.add(merchantAmt);
      // Cash on delivery: the merchant keeps the cash, so the commission is owed to the owner
      if ("COD".equals(p.getMethod())) codDue = codDue.add(fee);
    }
    return new Summary(list.size(), gross, commission, net, codDue);
  }
}
