package com.shop.payment.controller;

import com.shop.payment.domain.Plan;
import com.shop.payment.domain.PlatformSettings;
import com.shop.payment.service.BillingService;
import com.shop.payment.service.BillingService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
public class BillingController {

  public record SettingsRequest(@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal commissionPercent) {}
  public record SelectPlanRequest(@NotNull UUID planId) {}
  public record PlanRequest(@NotBlank @Size(max = 80) String name, @Size(max = 500) String description,
                            @NotNull @DecimalMin("0") BigDecimal price, @NotNull Plan.BillingPeriod billingPeriod,
                            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal commissionPercent,
                            @Min(1) Integer maxProducts, boolean active, int sortOrder) {
    PlanData data() { return new PlanData(name, description, price, billingPeriod, commissionPercent, maxProducts, active, sortOrder); }
  }

  private final BillingService billing;

  public BillingController(BillingService billing) { this.billing = billing; }

  // ---- public: pricing page ----

  @GetMapping("/plans")
  public List<Plan> plans() { return billing.activePlans(); }

  /** Called by product-service to enforce the plan's product limit. Not exposed without a login at the gateway. */
  @GetMapping("/limits/{merchantId}")
  public Limits limits(@PathVariable UUID merchantId) { return billing.limitsFor(merchantId); }

  // ---- merchant (store owner) ----

  @GetMapping("/subscription")
  public SubscriptionView subscription(@RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
    requireOwner(role);
    return billing.subscription(userId);
  }

  @PostMapping("/subscription/checkout")
  public CheckoutResult checkout(@Valid @RequestBody SelectPlanRequest req,
                                 @RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
    requireOwner(role);
    return billing.startSubscription(userId, req.planId());
  }

  @PostMapping("/subscription/verify")
  public SubscriptionView verify(@Valid @RequestBody PaymentController.VerifyRequest req,
                                 @RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
    requireOwner(role);
    return billing.confirmSubscription(userId, req.razorpayOrderId(), req.razorpayPaymentId(), req.razorpaySignature());
  }

  @GetMapping("/merchant/summary")
  public Summary mySummary(@RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
    requireOwner(role);
    return billing.merchantSummary(userId);
  }

  // ---- platform owner ----

  @GetMapping("/admin/settings")
  public PlatformSettings getSettings(@RequestHeader("X-User-Role") String role) {
    requireAdmin(role);
    return billing.settings();
  }

  @PutMapping("/admin/settings")
  public PlatformSettings putSettings(@Valid @RequestBody SettingsRequest req, @RequestHeader("X-User-Role") String role) {
    requireAdmin(role);
    return billing.updateSettings(req.commissionPercent());
  }

  @GetMapping("/admin/summary")
  public AdminSummary adminSummary(@RequestHeader("X-User-Role") String role) {
    requireAdmin(role);
    return billing.adminSummary();
  }

  @GetMapping("/admin/plans")
  public List<Plan> allPlans(@RequestHeader("X-User-Role") String role) {
    requireAdmin(role);
    return billing.allPlans();
  }

  @PostMapping("/admin/plans")
  @ResponseStatus(HttpStatus.CREATED)
  public Plan createPlan(@Valid @RequestBody PlanRequest req, @RequestHeader("X-User-Role") String role) {
    requireAdmin(role);
    return billing.savePlan(null, req.data());
  }

  @PutMapping("/admin/plans/{id}")
  public Plan updatePlan(@PathVariable UUID id, @Valid @RequestBody PlanRequest req, @RequestHeader("X-User-Role") String role) {
    requireAdmin(role);
    return billing.savePlan(id, req.data());
  }

  private void requireOwner(String role) {
    if (!"MERCHANT".equals(role) && !"ADMIN".equals(role))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the store owner can do this");
  }

  private void requireAdmin(String role) {
    if (!"ADMIN".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform owner access required");
  }
}
