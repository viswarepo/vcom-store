package com.shop.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.payment.service.PaymentService;
import com.shop.payment.RazorpayClient;
import com.shop.payment.domain.Payment;
import com.shop.payment.dto.CheckoutDetails;
import com.shop.payment.producer.OutcomePublisher;
import com.shop.payment.repository.PaymentRepository;
import com.shop.payment.service.BillingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

  private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

  public record PaymentDto(UUID id, UUID orderId, BigDecimal amount, String method, Payment.Status status,
                           String transactionId, String failureReason, Instant createdAt) {}

  /** What Razorpay Checkout hands back to the browser after a successful payment. */
  public record VerifyRequest(@NotBlank String razorpayPaymentId, String razorpayOrderId, @NotBlank String razorpaySignature) {}

  private final PaymentRepository payments;
  private final PaymentService service;
  private final BillingService billing;
  private final RazorpayClient razorpay;
  private final OutcomePublisher publisher;
  private final ObjectMapper mapper;

  public PaymentController(PaymentRepository payments, PaymentService service, BillingService billing,
                           RazorpayClient razorpay, OutcomePublisher publisher, ObjectMapper mapper) {
    this.payments = payments;
    this.service = service;
    this.billing = billing;
    this.razorpay = razorpay;
    this.publisher = publisher;
    this.mapper = mapper;
  }

  @GetMapping("/order/{orderId}")
  public PaymentDto forOrder(@PathVariable UUID orderId, @RequestHeader("X-User-Id") UUID userId,
                             @RequestHeader("X-User-Role") String role) {
    Payment p = payments.findByOrderId(orderId)
        .filter(x -> x.getCustomerId().equals(userId) || "ADMIN".equals(role))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No payment for this order"));
    return toDto(p);
  }

  @GetMapping("/order/{orderId}/checkout")
  public CheckoutDetails checkout(@PathVariable UUID orderId, @RequestHeader("X-User-Id") UUID userId) {
    return service.checkout(orderId, userId);
  }

  @PostMapping("/order/{orderId}/verify")
  public PaymentDto verify(@PathVariable UUID orderId, @Valid @RequestBody VerifyRequest req,
                           @RequestHeader("X-User-Id") UUID userId) {
    publisher.publish(service.confirm(orderId, userId, req.razorpayPaymentId(), req.razorpaySignature()));
    return toDto(payments.findByOrderId(orderId).orElseThrow());
  }

  @PostMapping("/order/{orderId}/abandon")
  public ResponseEntity<Void> abandon(@PathVariable UUID orderId, @RequestHeader("X-User-Id") UUID userId) {
    publisher.publish(service.abandon(orderId, userId));
    return ResponseEntity.noContent().build();
  }

  /**
   * Razorpay calls this (configure it in the Razorpay dashboard). It is reachable without a login,
   * so the only thing that makes a request trustworthy is the signature over the raw body.
   */
  @PostMapping("/webhooks/razorpay")
  public ResponseEntity<Void> webhook(@RequestBody byte[] rawBody,
                                      @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
    if (!razorpay.verifyWebhook(rawBody, signature)) return ResponseEntity.badRequest().build();
    try {
      JsonNode root = mapper.readTree(rawBody);
      String event = root.path("event").asText("");
      // payment.authorized may arrive first and webhooks can repeat, so only act on captured/paid
      if (event.equals("payment.captured") || event.equals("order.paid")) {
        JsonNode pay = root.path("payload").path("payment").path("entity");
        String rpOrder = pay.path("order_id").asText(null);
        String rpPayment = pay.path("id").asText(null);
        if (rpOrder != null && rpPayment != null) {
          publisher.publish(service.confirmFromWebhook(rpOrder, rpPayment));
          billing.confirmSubscriptionByOrder(rpOrder, rpPayment);
        }
      }
    } catch (IOException e) {
      log.warn("Unreadable Razorpay webhook: {}", e.getMessage());
      return ResponseEntity.badRequest().build();
    }
    return ResponseEntity.ok().build();
  }

  private PaymentDto toDto(Payment p) {
    return new PaymentDto(p.getId(), p.getOrderId(), p.getAmount(), p.getMethod(), p.getStatus(),
        p.getTransactionId(), p.getFailureReason(), p.getCreatedAt());
  }
}
