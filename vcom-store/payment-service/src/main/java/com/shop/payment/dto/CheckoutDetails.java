package com.shop.payment.dto;

/** What the browser needs to open Razorpay Checkout. Contains the public key id only. */
public record CheckoutDetails(String provider, String keyId, String razorpayOrderId, long amount, String currency) {}
