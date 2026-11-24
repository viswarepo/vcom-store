package com.shop.cart.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.shop.cart.dto.CartItem;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** One Redis hash per user: cart:{userId} -> { productId : CartItem JSON }. */
@Service
public class CartService {

  private static final int MAX_QTY = 99;
  private static final Duration TTL = Duration.ofDays(30);

  private final StringRedisTemplate redis;
  private final ObjectMapper mapper;

  public CartService(StringRedisTemplate redis, ObjectMapper mapper) {
    this.redis = redis;
    this.mapper = mapper;
  }

  private String key(UUID userId) { return "cart:" + userId; }

  public List<CartItem> list(UUID userId) {
    return redis.<String, String>opsForHash().values(key(userId)).stream()
        .map(this::read)
        .sorted(Comparator.comparingLong(CartItem::addedAt))
        .toList();
  }

  public List<CartItem> add(UUID userId, CartItem item) {
    Object existing = redis.opsForHash().get(key(userId), item.productId().toString());
    int qty = item.quantity();
    long addedAt = System.currentTimeMillis();
    if (existing != null) {
      CartItem old = read((String) existing);
      qty += old.quantity();
      addedAt = old.addedAt();
    }
    save(userId, new CartItem(item.productId(), item.name(), item.price(), item.imageUrl(), Math.min(qty, MAX_QTY), addedAt));
    return list(userId);
  }

  public List<CartItem> setQuantity(UUID userId, UUID productId, int quantity) {
    Object existing = redis.opsForHash().get(key(userId), productId.toString());
    if (existing != null) {
      if (quantity <= 0) redis.opsForHash().delete(key(userId), productId.toString());
      else save(userId, read((String) existing).withQuantity(Math.min(quantity, MAX_QTY)));
    }
    return list(userId);
  }

  public List<CartItem> remove(UUID userId, UUID productId) {
    redis.opsForHash().delete(key(userId), productId.toString());
    return list(userId);
  }

  public void clear(UUID userId) { redis.delete(key(userId)); }

  private void save(UUID userId, CartItem item) {
    try {
      redis.opsForHash().put(key(userId), item.productId().toString(), mapper.writeValueAsString(item));
      redis.expire(key(userId), TTL);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private CartItem read(String json) {
    try {
      return mapper.readValue(json, CartItem.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }
}
