package com.shop.auth.controller;

import com.shop.auth.config.KafkaConfig;
import com.shop.auth.domain.Phones;
import com.shop.auth.domain.Role;
import com.shop.auth.domain.User;
import com.shop.auth.dto.Dtos.*;
import com.shop.auth.domain.UserLookup;
import com.shop.auth.dto.Dtos;
import com.shop.auth.repository.UserRepository;
import com.shop.auth.service.JwtService;
import com.shop.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

  /** Merchants and admins sign in here; customers sign in under the merchant whose store they joined. */
  public static final UUID PLATFORM = new UUID(0L, 0L);

  private final UserRepository users;
  private final UserLookup lookup;
  private final PasswordResetService passwordResets;
  private final JwtService jwt;
  private final KafkaTemplate<String, Object> kafka;
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

  public AuthController(UserRepository users, UserLookup lookup, PasswordResetService passwordResets,
                        JwtService jwt, KafkaTemplate<String, Object> kafka) {
    this.users = users;
    this.lookup = lookup;
    this.passwordResets = passwordResets;
    this.jwt = jwt;
    this.kafka = kafka;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public Dtos.AuthResponse register(@Valid @RequestBody RegisterRequest req) {
    final Role role;
    final UUID tenant;
    if (req.merchantId() == null) {
      role = Role.MERCHANT;
      tenant = PLATFORM;
    } else {
      User merchant = users.findById(req.merchantId())
          .filter(m -> m.getRole() == Role.MERCHANT)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown store"));
      role = Role.CUSTOMER;
      tenant = merchant.getId();
    }

    String email = req.email().toLowerCase();
    String phone = Phones.normalize(req.phone())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid 10-digit Indian mobile number"));
    if (users.existsByEmailIgnoreCaseAndTenantId(email, tenant) || users.existsByPhoneAndTenantId(phone, tenant)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          role == Role.CUSTOMER ? "You already have an account with this store" : "That email or mobile number is already registered");
    }
    User u = new User();
    u.setName(req.name());
    u.setEmail(email);
    u.setPhone(phone);
    u.setPasswordHash(encoder.encode(req.password()));
    u.setRole(role);
    u.setTenantId(tenant);
    u = users.save(u);

    kafka.send(KafkaConfig.USER_REGISTERED, u.getId().toString(),
        new UserRegisteredEvent(u.getId(), u.getEmail(), u.getName(), u.getRole(), u.tenantForToken(), u.getPhone()));
    return new AuthResponse(jwt.generate(u), UserDto.from(u));
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest req) {
    UUID tenant = req.merchantId() == null ? PLATFORM : req.merchantId();
    User u = lookup.find(req.identifier(), tenant)
        .filter(x -> encoder.matches(req.password(), x.getPasswordHash()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email, mobile number or password"));
    return new AuthResponse(jwt.generate(u), UserDto.from(u));
  }

  // ---------------- forgot / reset / change password ----------------

  /** Always answers the same way, so it cannot be used to discover which accounts exist. */
  @PostMapping("/forgot-password")
  public MessageResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
    passwordResets.request(req.identifier(), req.merchantId());
    return new MessageResponse("If an account matches, we have sent a 6-digit code. It is valid for 10 minutes.");
  }

  @PostMapping("/reset-password")
  public MessageResponse resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
    passwordResets.reset(req.identifier(), req.merchantId(), req.otp(), req.newPassword());
    return new MessageResponse("Your password has been updated. You can sign in now.");
  }

  @PostMapping("/change-password")
  public MessageResponse changePassword(@Valid @RequestBody ChangePasswordRequest req, @RequestHeader("X-User-Id") UUID userId) {
    User u = users.findById(userId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found"));
    if (!encoder.matches(req.currentPassword(), u.getPasswordHash()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your current password is incorrect");
    u.setPasswordHash(encoder.encode(req.newPassword()));
    users.save(u);
    return new MessageResponse("Your password has been changed.");
  }

  // ---------------- store team ----------------

  /** The store team (owner and store admins) sees only the customers who signed up to their own store. */
  @GetMapping("/customers")
  public List<CustomerDto> customers(@RequestHeader("X-Tenant-Id") UUID tenantId, @RequestHeader("X-User-Role") String role) {
    if (!"MERCHANT".equals(role) && !"STORE_ADMIN".equals(role) && !"ADMIN".equals(role))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant access required");
    return users.findByTenantIdAndRoleOrderByCreatedAtDesc(tenantId, Role.CUSTOMER).stream()
        .map(c -> new CustomerDto(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getCreatedAt()))
        .toList();
  }

  // ---- store admins (staff), managed by the merchant who owns the store ----

  @PostMapping("/staff")
  @ResponseStatus(HttpStatus.CREATED)
  public StaffDto addStaff(@Valid @RequestBody StaffRequest req,
                           @RequestHeader("X-User-Id") UUID ownerId, @RequestHeader("X-User-Role") String role) {
    requireOwner(role);
    String email = req.email().toLowerCase();
    // Staff sign in through the merchant login, so their email must be unique there
    if (users.existsByEmailIgnoreCaseAndTenantId(email, PLATFORM))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is already registered");
    User u = new User();
    u.setName(req.name());
    u.setEmail(email);
    u.setPasswordHash(encoder.encode(req.password()));
    u.setRole(Role.STORE_ADMIN);
    u.setTenantId(PLATFORM);
    u.setMerchantId(ownerId);
    return StaffDto.from(users.save(u));
  }

  @GetMapping("/staff")
  public List<StaffDto> staff(@RequestHeader("X-User-Id") UUID ownerId, @RequestHeader("X-User-Role") String role) {
    requireOwner(role);
    return users.findByMerchantIdAndRoleOrderByCreatedAtDesc(ownerId, Role.STORE_ADMIN).stream().map(StaffDto::from).toList();
  }

  @DeleteMapping("/staff/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeStaff(@PathVariable UUID id,
                          @RequestHeader("X-User-Id") UUID ownerId, @RequestHeader("X-User-Role") String role) {
    requireOwner(role);
    User u = users.findById(id)
        .filter(x -> x.getRole() == Role.STORE_ADMIN && ownerId.equals(x.getMerchantId()))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff member not found"));
    users.delete(u);
  }

  private void requireOwner(String role) {
    if (!"MERCHANT".equals(role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the store owner can manage staff");
  }
}
