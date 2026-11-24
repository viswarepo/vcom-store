package com.shop.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Minimal Razorpay client: create an order server-side, verify the signature Checkout returns,
 * and verify webhooks. The key secret never leaves this service; the browser only gets the key id.
 */
@Component
public class RazorpayClient {

  private final String keyId;
  private final String keySecret;
  private final String webhookSecret;
  private final RestClient rest;

  public RazorpayClient(@Value("${app.razorpay.key-id:}") String keyId,
                        @Value("${app.razorpay.key-secret:}") String keySecret,
                        @Value("${app.razorpay.webhook-secret:}") String webhookSecret) {
    this.keyId = keyId;
    this.keySecret = keySecret;
    this.webhookSecret = webhookSecret;
    this.rest = enabled()
        ? RestClient.builder().baseUrl("https://api.razorpay.com/v1").defaultHeaders(h -> h.setBasicAuth(keyId, keySecret)).build()
        : null;
  }

  /** False until RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET are set; the mock gateway is used instead. */
  public boolean enabled() { return !keyId.isBlank() && !keySecret.isBlank(); }

  public String keyId() { return keyId; }

  /** Amount is in paise. Razorpay refuses payments that have no order, so every payment starts here. */
  @SuppressWarnings("unchecked")
  public String createOrder(long amountPaise, String receipt, Map<String, String> notes) {
    Map<String, Object> body = Map.of("amount", amountPaise, "currency", "INR", "receipt", receipt, "notes", notes);
    Map<String, Object> resp = rest.post().uri("/orders").contentType(MediaType.APPLICATION_JSON).body(body)
        .retrieve().body(Map.class);
    Object id = resp == null ? null : resp.get("id");
    if (id == null) throw new IllegalStateException("Razorpay did not return an order id");
    return id.toString();
  }

  /** HMAC-SHA256 of "order_id|payment_id" with the key secret. Pass OUR stored order id, not the browser's. */
  public boolean verifyCheckoutSignature(String orderId, String paymentId, String signature) {
    if (!enabled() || orderId == null || paymentId == null) return false;
    String expected = hmacHex(keySecret, (orderId + "|" + paymentId).getBytes(StandardCharsets.UTF_8));
    return safeEquals(expected, signature);
  }

  /** HMAC-SHA256 of the RAW request body with the webhook secret (a different secret from the API key). */
  public boolean verifyWebhook(byte[] rawBody, String signature) {
    if (webhookSecret.isBlank()) return false;
    return safeEquals(hmacHex(webhookSecret, rawBody), signature);
  }

  private static String hmacHex(String secret, byte[] data) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(data));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  private static boolean safeEquals(String a, String b) {
    return a != null && b != null && MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
