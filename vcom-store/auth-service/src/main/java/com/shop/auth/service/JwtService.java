package com.shop.auth.service;

import com.shop.auth.domain.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final SecretKey key;
  private final Duration ttl;

  public JwtService(@Value("${app.jwt.secret}") String secret,
                    @Value("${app.jwt.ttl-minutes:120}") long ttlMinutes) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.ttl = Duration.ofMinutes(ttlMinutes);
  }

  public String generate(User user) {
    long now = System.currentTimeMillis();
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("role", user.getRole().name())
        .claim("tenant", user.tenantForToken().toString())
        .claim("name", user.getName())
        .claim("phone", user.getPhone() == null ? "" : user.getPhone())
        .issuedAt(new Date(now))
        .expiration(new Date(now + ttl.toMillis()))
        .signWith(key)
        .compact();
  }
}
