package com.shop.payment.service;

import com.shop.payment.PaymentGateway;
import com.shop.payment.RazorpayClient;
import com.shop.payment.domain.Payment;
import com.shop.payment.dto.CheckoutDetails;
import com.shop.payment.event.Events.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.shop.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Online payments (method ONLINE) go through Razorpay when keys are configured:
 * the order is created here, the customer pays in Razorpay Checkout, and the result is confirmed by the
 * signed browser callback or the signed webhook, whichever arrives first. Cash on delivery, and online
 * payments without Razorpay keys (development), complete immediately through the mock gateway.
 */
@Service
public class PaymentService {

  private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

  private final PaymentRepository payments;
  private final PaymentGateway gateway;
  private final BillingService billing;
  private final RazorpayClient razorpay;
  private final Duration expiry;

  public PaymentService(PaymentRepository payments, PaymentGateway gateway, BillingService billing,
                        RazorpayClient razorpay, @Value("${app.payment.expiry-minutes:15}") long expiryMinutes) {
    this.payments = payments;
    this.gateway = gateway;
    this.billing = billing;
    this.razorpay = razorpay;
    this.expiry = Duration.ofMinutes(expiryMinutes);
  }

  /** Called when stock is reserved. Returns null for a duplicate, or while waiting for the customer to pay. */
  @Transactional
  public Outcome process(InventoryReserved e) {
    if (payments.findByOrderId(e.orderId()).isPresent()) return null;

    Payment p = new Payment();
    p.setOrderId(e.orderId());
    p.setCustomerId(e.customerId());
    p.setAmount(e.total());
    p.setMethod(e.paymentMethod());
    // Platform owner's cut, using the commission rate in force right now
    BigDecimal rate = billing.commissionFor(e.merchantId());
    BigDecimal fee = e.total().multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    p.setMerchantId(e.merchantId());
    p.setCommissionPercent(rate);
    p.setPlatformFee(fee);
    p.setMerchantAmount(e.total().subtract(fee));

    if (razorpay.enabled() && "ONLINE".equals(e.paymentMethod())) {
      try {
        String receipt = "o_" + e.orderId().toString().replace("-", "");
        String rpOrder = razorpay.createOrder(toPaise(e.total()), receipt, Map.of("orderId", e.orderId().toString()));
        p.setStatus(Payment.Status.PENDING);
        p.setRazorpayOrderId(rpOrder);
        p.setExpiresAt(Instant.now().plus(expiry));
        payments.save(p);
        return null;   // the customer pays in the browser; confirm()/webhook completes it
      } catch (RuntimeException ex) {
        log.error("Could not create Razorpay order for {}: {}", e.orderId(), ex.getMessage());
        p.setStatus(Payment.Status.FAILED);
        p.setFailureReason("Could not start the payment");
        payments.save(p);
        return new PaymentFailed(e.orderId(), "Could not start the payment. Please try again.");
      }
    }

    var result = gateway.charge(e.orderId(), e.total(), e.paymentMethod());
    p.setStatus(result.success() ? Payment.Status.SUCCEEDED : Payment.Status.FAILED);
    p.setTransactionId(result.transactionId());
    p.setFailureReason(result.failureReason());
    if (result.success()) p.setPaidAt(Instant.now());
    p = payments.save(p);
    return result.success()
        ? new PaymentCompleted(e.orderId(), p.getId().toString(), result.transactionId())
        : new PaymentFailed(e.orderId(), result.failureReason());
  }

  @Transactional(readOnly = true)
  public CheckoutDetails checkout(UUID orderId, UUID userId) {
    Payment p = owned(orderId, userId);
    if (p.getStatus() != Payment.Status.PENDING || p.getRazorpayOrderId() == null)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "No payment is waiting for this order");
    return new CheckoutDetails("razorpay", razorpay.keyId(), p.getRazorpayOrderId(), toPaise(p.getAmount()), "INR");
  }

  /** The browser reports success. We trust it only if the signature matches OUR stored Razorpay order id. */
  @Transactional
  public Outcome confirm(UUID orderId, UUID userId, String razorpayPaymentId, String signature) {
    Payment p = owned(orderId, userId);
    if (p.getRazorpayOrderId() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This order is not paid through Razorpay");
    if (!razorpay.verifyCheckoutSignature(p.getRazorpayOrderId(), razorpayPaymentId, signature))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment verification failed");
    return complete(p, razorpayPaymentId);
  }

  /** Razorpay told us (signed webhook) that this order was paid. */
  @Transactional
  public Outcome confirmFromWebhook(String razorpayOrderId, String razorpayPaymentId) {
    return payments.findByRazorpayOrderId(razorpayOrderId).map(p -> complete(p, razorpayPaymentId)).orElse(null);
  }

  /** The customer gave up on paying. */
  @Transactional
  public Outcome abandon(UUID orderId, UUID userId) {
    Payment p = owned(orderId, userId);
    if (p.getStatus() != Payment.Status.PENDING) return null;
    p.setStatus(Payment.Status.FAILED);
    p.setFailureReason("Payment cancelled");
    return new PaymentFailed(orderId, "Payment cancelled");
  }

  /** Unpaid online orders are cancelled so their stock goes back on sale. */
  @Transactional
  public List<Outcome> expireStale() {
    List<Outcome> out = new ArrayList<>();
    for (Payment p : payments.findByStatusAndExpiresAtBefore(Payment.Status.PENDING, Instant.now())) {
      p.setStatus(Payment.Status.FAILED);
      p.setFailureReason("Payment not completed in time");
      out.add(new PaymentFailed(p.getOrderId(), "Payment not completed in time"));
    }
    return out;
  }

  private Outcome complete(Payment p, String razorpayPaymentId) {
    if (p.getStatus() == Payment.Status.SUCCEEDED) return null;   // already done: browser and webhook both report
    if (p.getStatus() == Payment.Status.FAILED) {
      // Money arrived after we gave up on the order: it needs a refund (not automated yet)
      p.setRazorpayPaymentId(razorpayPaymentId);
      p.setFailureReason("Paid after the order was cancelled: refund needed");
      log.warn("Payment {} for cancelled order {} needs a refund", razorpayPaymentId, p.getOrderId());
      return null;
    }
    p.setStatus(Payment.Status.SUCCEEDED);
    p.setPaidAt(Instant.now());
    p.setRazorpayPaymentId(razorpayPaymentId);
    p.setTransactionId(razorpayPaymentId);
    return new PaymentCompleted(p.getOrderId(), p.getId().toString(), razorpayPaymentId);
  }

  private Payment owned(UUID orderId, UUID userId) {
    return payments.findByOrderId(orderId)
        .filter(p -> p.getCustomerId().equals(userId))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No payment for this order"));
  }

  static long toPaise(BigDecimal rupees) {
    return rupees.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
  }
}
