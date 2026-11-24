package com.shop.auth.config;

import com.shop.auth.domain.User;

/**
 * Delivers a text message to a user: by SMS when they have a mobile number, otherwise by email.
 * The default implementation only writes to the log (development). Add your own bean for production
 * with app.messaging.provider set to anything other than "log".
 */
public interface MessageSender {
  void send(User recipient, String text);
}
