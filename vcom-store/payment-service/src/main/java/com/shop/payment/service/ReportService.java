package com.shop.payment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.shop.payment.domain.Payment;
import com.shop.payment.domain.SubscriptionPayment;
import com.shop.payment.repository.PaymentRepository;
import com.shop.payment.repository.SubscriptionPaymentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Sales reports for a merchant or for the whole platform, grouped by day, month, quarter or year.
 * Dates are read in the configured time zone. Quarters and years follow the financial year
 * (default April to March, as in India); set app.reports.fiscal-year-start-month to 1 for calendar years.
 */
@Service
public class ReportService {

  public enum Granularity { DAY, MONTH, QUARTER, YEAR }

  public record Config(String zone, int fiscalYearStartMonth) {}
  public record Totals(long orders, BigDecimal gross, BigDecimal commission, BigDecimal net, BigDecimal subscriptions,
                       BigDecimal averageOrder, BigDecimal codCommissionDue) {}
  public record Period(String label, LocalDate start, LocalDate end, long orders, BigDecimal gross,
                       BigDecimal commission, BigDecimal net, BigDecimal subscriptions) {}
  public record MethodRow(String method, long orders, BigDecimal gross) {}
  public record TopMerchant(UUID merchantId, long orders, BigDecimal gross, BigDecimal commission) {}
  public record Report(LocalDate from, LocalDate to, Granularity granularity, Config config, Totals totals,
                       List<Period> periods, List<MethodRow> byMethod, List<TopMerchant> topMerchants) {}

  private static final int MAX_PERIODS = 400;
  private static final Instant FAR_FUTURE = Instant.parse("9999-01-01T00:00:00Z");

  private final PaymentRepository payments;
  private final SubscriptionPaymentRepository subscriptionPayments;
  private final ZoneId zone;
  private final int fyStart;

  public ReportService(PaymentRepository payments, SubscriptionPaymentRepository subscriptionPayments,
                       @Value("${app.reports.zone:Asia/Kolkata}") String zone,
                       @Value("${app.reports.fiscal-year-start-month:4}") int fiscalYearStartMonth) {
    this.payments = payments;
    this.subscriptionPayments = subscriptionPayments;
    this.zone = ZoneId.of(zone);
    this.fyStart = Math.max(1, Math.min(12, fiscalYearStartMonth));
  }

  public Config config() { return new Config(zone.getId(), fyStart); }

  /** merchantId == null gives the platform-wide report for the owner. */
  @Transactional(readOnly = true)
  public Report report(UUID merchantId, LocalDate from, LocalDate to, Granularity g) {
    if (from != null && to != null && to.isBefore(from))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The end date is before the start date");

    Instant lo = from == null ? Instant.EPOCH : from.atStartOfDay(zone).toInstant();
    Instant hi = to == null ? FAR_FUTURE : to.plusDays(1).atStartOfDay(zone).toInstant();

    List<Payment> sales = merchantId == null
        ? payments.findPaidBetween(Payment.Status.SUCCEEDED, lo, hi)
        : payments.findPaidForMerchant(Payment.Status.SUCCEEDED, merchantId, lo, hi);
    sales = sales.stream().filter(p -> p.getMerchantId() != null).toList();
    List<SubscriptionPayment> subs = merchantId != null ? List.<SubscriptionPayment>of()
        : subscriptionPayments.findByStatus("PAID").stream()
            .filter(s -> !when(s).isBefore(lo) && when(s).isBefore(hi)).toList();

    // bucket by period start
    Map<LocalDate, Acc> buckets = new HashMap<>();
    Totals totals = totals(sales, subs);
    for (Payment p : sales) buckets.computeIfAbsent(periodStart(day(when(p)), g), k -> new Acc()).add(p);
    for (SubscriptionPayment s : subs) {
      Acc a = buckets.computeIfAbsent(periodStart(day(when(s)), g), k -> new Acc());
      a.subs = a.subs.add(s.getAmount());
    }

    // list every period in the range, including empty ones, so charts show gaps as zero
    LocalDate first = from != null ? from : buckets.keySet().stream().min(Comparator.naturalOrder()).orElse(null);
    LocalDate last = to != null ? to : LocalDate.now(zone);
    List<Period> periods = new ArrayList<>();
    if (first != null) {
      LocalDate cur = periodStart(first, g);
      LocalDate stop = periodStart(last, g);
      while (!cur.isAfter(stop)) {
        if (periods.size() >= MAX_PERIODS)
          throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many periods. Choose a shorter range or a larger interval.");
        Acc a = buckets.getOrDefault(cur, new Acc());
        periods.add(new Period(label(cur, g), cur, next(cur, g).minusDays(1), a.orders, a.gross, a.commission, a.net, a.subs));
        cur = next(cur, g);
      }
    }

    List<MethodRow> byMethod = sales.stream()
        .collect(Collectors.groupingBy(p -> "COD".equals(p.getMethod()) ? "Cash on delivery" : "Online")).entrySet().stream()
        .map(e -> new MethodRow(e.getKey(), e.getValue().size(), sum(e.getValue(), Payment::getAmount)))
        .sorted(Comparator.comparing(MethodRow::gross).reversed()).toList();

    List<TopMerchant> top = merchantId != null ? List.of()
        : sales.stream().collect(Collectors.groupingBy(Payment::getMerchantId)).entrySet().stream()
            .map(e -> new TopMerchant(e.getKey(), e.getValue().size(), sum(e.getValue(), Payment::getAmount),
                sum(e.getValue(), p -> p.getPlatformFee() == null ? BigDecimal.ZERO : p.getPlatformFee())))
            .sorted(Comparator.comparing(TopMerchant::gross).reversed()).limit(10).toList();

    return new Report(from, to, g, config(), totals, periods, byMethod, top);
  }

  // ---------------- helpers ----------------

  private static final class Acc {
    long orders;
    BigDecimal gross = BigDecimal.ZERO, commission = BigDecimal.ZERO, net = BigDecimal.ZERO, subs = BigDecimal.ZERO;

    void add(Payment p) {
      orders++;
      gross = gross.add(p.getAmount());
      commission = commission.add(p.getPlatformFee() == null ? BigDecimal.ZERO : p.getPlatformFee());
      net = net.add(p.getMerchantAmount() == null ? p.getAmount() : p.getMerchantAmount());
    }
  }

  private Totals totals(List<Payment> sales, List<SubscriptionPayment> subs) {
    BigDecimal gross = sum(sales, Payment::getAmount);
    BigDecimal commission = sum(sales, p -> p.getPlatformFee() == null ? BigDecimal.ZERO : p.getPlatformFee());
    BigDecimal net = sum(sales, p -> p.getMerchantAmount() == null ? p.getAmount() : p.getMerchantAmount());
    BigDecimal codDue = sum(sales.stream().filter(p -> "COD".equals(p.getMethod())).toList(),
        p -> p.getPlatformFee() == null ? BigDecimal.ZERO : p.getPlatformFee());
    BigDecimal subscriptions = subs.stream().map(SubscriptionPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal avg = sales.isEmpty() ? BigDecimal.ZERO : gross.divide(BigDecimal.valueOf(sales.size()), 2, RoundingMode.HALF_UP);
    return new Totals(sales.size(), gross, commission, net, subscriptions, avg, codDue);
  }

  private static BigDecimal sum(List<Payment> list, java.util.function.Function<Payment, BigDecimal> f) {
    return list.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private static Instant when(Payment p) { return p.getPaidAt() != null ? p.getPaidAt() : p.getCreatedAt(); }
  private static Instant when(SubscriptionPayment s) { return s.getPaidAt() != null ? s.getPaidAt() : s.getCreatedAt(); }
  private LocalDate day(Instant t) { return t.atZone(zone).toLocalDate(); }

  private LocalDate fiscalYearStart(LocalDate d) {
    int year = d.getMonthValue() >= fyStart ? d.getYear() : d.getYear() - 1;
    return LocalDate.of(year, fyStart, 1);
  }

  private LocalDate periodStart(LocalDate d, Granularity g) {
    return switch (g) {
      case DAY -> d;
      case MONTH -> d.withDayOfMonth(1);
      case YEAR -> fiscalYearStart(d);
      case QUARTER -> {
        int monthsIn = Math.floorMod(d.getMonthValue() - fyStart, 12);
        yield fiscalYearStart(d).plusMonths((monthsIn / 3) * 3L);
      }
    };
  }

  private LocalDate next(LocalDate start, Granularity g) {
    return switch (g) {
      case DAY -> start.plusDays(1);
      case MONTH -> start.plusMonths(1);
      case QUARTER -> start.plusMonths(3);
      case YEAR -> start.plusYears(1);
    };
  }

  private String label(LocalDate start, Granularity g) {
    return switch (g) {
      case DAY -> start.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH));
      case MONTH -> start.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH));
      case YEAR -> fyStart == 1 ? String.valueOf(start.getYear()) : "FY " + fyName(start);
      case QUARTER -> {
        int q = Math.floorMod(start.getMonthValue() - fyStart, 12) / 3 + 1;
        LocalDate end = start.plusMonths(2);
        String months = start.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)) + "–"
            + end.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH));
        yield fyStart == 1 ? "Q" + q + " " + start.getYear() + " (" + months + ")"
            : "Q" + q + " FY " + fyName(fiscalYearStart(start)) + " (" + months + ")";
      }
    };
  }

  private String fyName(LocalDate fyStartDate) {
    int y = fyStartDate.getYear();
    return y + "-" + String.format("%02d", (y + 1) % 100);
  }
}
