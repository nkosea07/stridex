package com.racetracker.service;

import com.racetracker.model.Payment;

import java.math.BigDecimal;

/**
 * Strategy for a payment provider. Implementations are resolved by {@link #key()} matching
 * the {@code Payment.provider} value, so new providers can be added without touching callers.
 */
public interface PaymentProvider {

    String key();

    /**
     * Requests a provider-side refund and returns the provider's refund reference (or null).
     * Must throw a domain exception when the provider rejects the request.
     */
    String refund(Payment payment, BigDecimal amount, String reason);
}
