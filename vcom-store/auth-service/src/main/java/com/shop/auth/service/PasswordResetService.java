package com.shop.auth.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.shop.auth.config.MessageSender;
import com.shop.auth.controller.AuthController;
import com.shop.auth.domain.PasswordReset;
import com.shop.auth.domain.User;
import com.shop.auth.domain.UserLookup;
import com.shop.auth.repository.PasswordResetRepository;
import com.shop.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Forgot password with a 6-digit one-time code sent to the account's mobile number (or email if it has no number).
 * Codes last 10 minutes, allow 5 wrong tries, can be requested once a minute and 5 times an hour, and the
 * request endpoint answers the same way whether or not the account exists, so it cannot be used to find accounts.
 */
@Service
public class PasswordResetService {

  private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
  private static final Duration TTL = Duration.ofMinutes(10);
  private static final Duration COOLDOWN = Duration.ofSeconds(60);
  private static final int MAX_PER_HOUR = 5;
  private static final int MAX_ATTEMPTS = 5;

  private final UserLookup lookup;
  private final UserRepository users;
  private final PasswordResetRepository resets;
  private final MessageSender sender;
  private final SecureRandom random = new SecureRandom();
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public PasswordResetService(UserLookup lookup, UserRepository users, PasswordResetRepository resets, MessageSender sender) {
    this.lookup = lookup;
    this.users = users;
    this.resets = resets;
    this.sender = sender;
  }

  @Transactional
  public void request(String identifier, UUID merchantId) {
    UUID tenant = merchantId == null ? AuthController.PLATFORM : merchantId;
    Optional<User> found = lookup.find(identifier, tenant);
    if (found.isEmpty()) return;   // say nothing: the caller gets the same answer either way
    User user = found.get();
    Instant now = Instant.now();

    var last = resets.findFirstByUserIdOrderByCreatedAtDesc(user.getId());
    if (last.isPresent() && last.get().getCreatedAt().isAfter(now.minus(COOLDOWN))) return;
    if (resets.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(Duration.ofHours(1))) >= MAX_PER_HOUR) return;

    resets.findByUserIdAndUsedFalse(user.getId()).forEach(r -> r.setUsed(true));   // a new code replaces older ones
    String otp = String.format("%06d", random.nextInt(1_000_000));
    resets.save(new PasswordReset(user.getId(), encoder.encode(otp), now.plus(TTL)));
    try {
      sender.send(user, "Your vcom-store password reset code is " + otp + ". It expires in 10 minutes. If you did not ask for it, ignore this message.");
    } catch (RuntimeException e) {
      log.error("Could not deliver the reset code for user {}: {}", user.getId(), e.getMessage());
    }
  }

  /** Wrong attempts must be counted even though the request fails, so this does not roll back on the error. */
  @Transactional(noRollbackFor = ResponseStatusException.class)
  public void reset(String identifier, UUID merchantId, String otp, String newPassword) {
    UUID tenant = merchantId == null ? AuthController.PLATFORM : merchantId;
    User user = lookup.find(identifier, tenant).orElseThrow(PasswordResetService::invalid);
    PasswordReset pr = resets.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(user.getId()).orElseThrow(PasswordResetService::invalid);

    if (pr.getExpiresAt().isBefore(Instant.now()) || pr.getAttempts() >= MAX_ATTEMPTS) {
      pr.setUsed(true);
      throw invalid();
    }
    if (!encoder.matches(otp, pr.getOtpHash())) {
      pr.setAttempts(pr.getAttempts() + 1);
      throw invalid();
    }
    user.setPasswordHash(encoder.encode(newPassword));
    users.save(user);
    resets.findByUserIdAndUsedFalse(user.getId()).forEach(r -> r.setUsed(true));
  }

  private static ResponseStatusException invalid() {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, "That code is invalid or has expired");
  }
}
