package com.shop.auth.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/** Indian mobile numbers, stored in one canonical form: +91 followed by 10 digits starting 6 to 9. */
public final class Phones {

  private static final Pattern MOBILE = Pattern.compile("^[6-9]\\d{9}$");

  private Phones() {}

  /** Accepts 9876543210, 09876543210, 919876543210, +91 98765 43210 and similar. Empty if it is not valid. */
  public static Optional<String> normalize(String raw) {
    if (raw == null) return Optional.empty();
    String d = raw.replaceAll("[\\s\\-()]", "");
    if (d.startsWith("+91")) d = d.substring(3);
    else if (d.startsWith("91") && d.length() == 12) d = d.substring(2);
    else if (d.startsWith("0") && d.length() == 11) d = d.substring(1);
    return MOBILE.matcher(d).matches() ? Optional.of("+91" + d) : Optional.empty();
  }
}
