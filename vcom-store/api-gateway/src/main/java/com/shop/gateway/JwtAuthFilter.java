package com.shop.gateway;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Validates the JWT, strips any client-supplied X-User-* headers (anti-spoofing)
 * and forwards the verified identity to downstream services.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

  private final SecretKey key;

  public JwtAuthFilter(@Value("${app.jwt.secret}") String secret) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest req = exchange.getRequest();
    String path = req.getURI().getPath();
    HttpMethod method = req.getMethod();

    boolean publicRead = HttpMethod.GET.equals(method)
        && !path.endsWith("/mine")
        && !path.equals("/api/orders") && !path.startsWith("/api/orders/")
        && (path.startsWith("/api/products") || path.startsWith("/api/stores") || path.startsWith("/api/inventory") || path.equals("/api/payments/plans"));
    boolean isPublic = HttpMethod.OPTIONS.equals(method) || path.startsWith("/api/auth/")
        || path.startsWith("/api/payments/webhooks/")   // Razorpay calls this; the signature is the authentication
        || publicRead;

    ServerHttpRequest.Builder builder = req.mutate().headers(h -> {
      h.remove("X-User-Id");
      h.remove("X-User-Role");
      h.remove("X-Tenant-Id");
      h.remove("X-User-Email");
      h.remove("X-User-Name");
      h.remove("X-User-Phone");
    });

    String auth = req.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
    if (auth != null && auth.startsWith("Bearer ")) {
      try {
        Claims claims = Jwts.parser().verifyWith(key).build()
            .parseSignedClaims(auth.substring(7)).getPayload();
        builder.header("X-User-Id", claims.getSubject());
        builder.header("X-User-Role", String.valueOf(claims.get("role")));
        builder.header("X-Tenant-Id", String.valueOf(claims.get("tenant")));
        // URL-encoded so names with non-ASCII characters are safe in HTTP headers
        builder.header("X-User-Email", URLEncoder.encode(String.valueOf(claims.get("email")), StandardCharsets.UTF_8));
        builder.header("X-User-Name", URLEncoder.encode(String.valueOf(claims.get("name")), StandardCharsets.UTF_8));
        builder.header("X-User-Phone", URLEncoder.encode(String.valueOf(claims.get("phone")), StandardCharsets.UTF_8));
      } catch (Exception e) {
        if (!isPublic) return unauthorized(exchange);
      }
    } else if (!isPublic) {
      return unauthorized(exchange);
    }
    return chain.filter(exchange.mutate().request(builder.build()).build());
  }

  private Mono<Void> unauthorized(ServerWebExchange exchange) {
    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
    return exchange.getResponse().setComplete();
  }

  @Override
  public int getOrder() { return -1; }
}
