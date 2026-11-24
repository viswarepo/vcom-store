package com.shop.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.shop.auth.domain.Role;
import com.shop.auth.domain.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class Dtos {
  private Dtos() {}

  /** merchantId == null: a merchant signing up. merchantId set: a customer joining that merchant's store. */
  public record RegisterRequest(
      @NotBlank String name,
      @NotBlank @Email String email,
      @NotBlank String phone,
      @NotBlank @Size(min = 8, max = 72) String password,
      UUID merchantId) {}

  /** identifier is an email address or a mobile number ("email" is still accepted as the field name). */
  public record LoginRequest(@NotBlank @JsonAlias("email") String identifier, @NotBlank String password, UUID merchantId) {}

  public record UserDto(UUID id, String name, String email, String phone, Role role, UUID tenantId) {
    public static UserDto from(User u) { return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.tenantForToken()); }
  }

  public record CustomerDto(UUID id, String name, String email, String phone, Instant createdAt) {}

  public record AuthResponse(String token, UserDto user) {}

  public record UserRegisteredEvent(UUID userId, String email, String name, Role role, UUID tenantId, String phone) {}

  public record StaffRequest(
      @NotBlank String name,
      @NotBlank @Email String email,
      @NotBlank @Size(min = 6) String password) {}

  public record StaffDto(UUID id, String name, String email, Instant createdAt) {
    public static StaffDto from(User u) { return new StaffDto(u.getId(), u.getName(), u.getEmail(), u.getCreatedAt()); }
  }

  // ---- forgot / reset / change password ----

  public record ForgotPasswordRequest(@NotBlank String identifier, UUID merchantId) {}

  public record ResetPasswordRequest(
      @NotBlank String identifier,
      UUID merchantId,
      @NotBlank @Pattern(regexp = "\\d{6}", message = "must be the 6-digit code") String otp,
      @NotBlank @Size(min = 8, max = 72) String newPassword) {}

  public record ChangePasswordRequest(
      @NotBlank String currentPassword,
      @NotBlank @Size(min = 8, max = 72) String newPassword) {}

  public record MessageResponse(String message) {}
}
