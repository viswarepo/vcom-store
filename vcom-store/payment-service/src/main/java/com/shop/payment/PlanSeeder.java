package com.shop.payment;

import java.math.BigDecimal;

import com.shop.payment.domain.Plan;
import com.shop.payment.repository.PlanRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** First start only: creates example plans. Edit or replace them at /admin. The prices are placeholders. */
@Component
public class PlanSeeder implements ApplicationRunner {

  private final PlanRepository plans;

  public PlanSeeder(PlanRepository plans) { this.plans = plans; }

  @Override
  public void run(ApplicationArguments args) {
    if (plans.count() > 0) return;
    plans.save(plan("Starter", "Try selling online with no monthly cost.", "0", Plan.BillingPeriod.MONTHLY, "10", 10, 1));
    plans.save(plan("Growth", "For stores that are taking off.", "499", Plan.BillingPeriod.MONTHLY, "7", 200, 2));
    plans.save(plan("Pro", "Lowest commission, unlimited products. Billed yearly.", "4999", Plan.BillingPeriod.YEARLY, "5", null, 3));
  }

  private static Plan plan(String name, String description, String price, Plan.BillingPeriod period,
                           String commission, Integer maxProducts, int order) {
    Plan p = new Plan();
    p.setName(name);
    p.setDescription(description);
    p.setPrice(new BigDecimal(price));
    p.setBillingPeriod(period);
    p.setCommissionPercent(new BigDecimal(commission));
    p.setMaxProducts(maxProducts);
    p.setSortOrder(order);
    return p;
  }
}
