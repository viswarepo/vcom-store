package com.shop.auth.config;

import com.shop.auth.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** DEVELOPMENT ONLY: prints the message, including any one-time code, to the auth-service log. */
@Component
@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "log", matchIfMissing = true)
public class LoggingMessageSender implements MessageSender {

  private static final Logger log = LoggerFactory.getLogger(LoggingMessageSender.class);

  @Override
  public void send(User recipient, String text) {
    String channel = recipient.getPhone() != null ? "SMS to " + recipient.getPhone() : "email to " + recipient.getEmail();
    log.warn("[DEV MESSAGE] {} -> {}", channel, text);
  }
}
