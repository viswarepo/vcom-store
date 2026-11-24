package com.shop.order.domain;

/** Delivery progress of a paid order. Only moves forward. */
public enum FulfillmentStatus { NEW, PACKED, SHIPPED, DELIVERED }
