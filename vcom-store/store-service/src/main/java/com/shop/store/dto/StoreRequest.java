package com.shop.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StoreRequest(
    @NotBlank String name,
    String description,
    String logoUrl,
    @Size(max = 1000) String address,
    @Size(max = 20) String gstin,
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "must be a hex colour like #1f6f5c") String themeColor) {}
