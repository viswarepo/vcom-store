package com.shop.payment;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Development gateway: CARD and COD succeed, TEST_DECLINE always fails so you can exercise the rollback path. */
@Component
public class MockPaymentGateway implements PaymentGateway {
  @Override
  public Result charge(UUID orderId, BigDecimal amount, String method) {
    if ("TEST_DECLINE".equals(method)) return new Result(false, null, "Card declined (test)");
    String prefix = "COD".equals(method) ? "cod_" : "mock_";
    return new Result(true, prefix + UUID.randomUUID().toString().substring(0, 12), null);
  }
}
