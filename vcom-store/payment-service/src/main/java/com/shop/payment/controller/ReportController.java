package com.shop.payment.controller;

import com.shop.payment.service.ReportService;
import com.shop.payment.service.ReportService.*;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments/reports")
public class ReportController {

  private final ReportService reports;

  public ReportController(ReportService reports) { this.reports = reports; }

  /** Time zone and financial-year start, so the screen can offer matching presets such as "This quarter". */
  @GetMapping("/config")
  public Config config() { return reports.config(); }

  /** The store owner's own sales. Money figures, so store admins (staff) are excluded. */
  @GetMapping("/merchant")
  public Report merchant(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(defaultValue = "MONTH") Granularity granularity,
                         @RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
    if (!"MERCHANT".equals(role) && !"ADMIN".equals(role))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the store owner can view sales reports");
    return reports.report(userId, from, to, granularity);
  }

  /** The platform owner's report across all merchants. */
  @GetMapping("/admin")
  public Report admin(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      @RequestParam(defaultValue = "MONTH") Granularity granularity,
                      @RequestHeader("X-User-Role") String role) {
    if (!"ADMIN".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform owner access required");
    return reports.report(null, from, to, granularity);
  }
}
