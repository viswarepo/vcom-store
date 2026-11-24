package com.shop.product.event;

import java.time.Instant;
import java.util.UUID;

public record ProductEvent(UUID productId, String type, Instant at) {}
