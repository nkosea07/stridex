package com.racetracker.service.impl;

import com.racetracker.exception.ApiException;
import com.racetracker.model.Payment;
import com.racetracker.service.PaymentProvider;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class PesepayPaymentProvider implements PaymentProvider {

    public static final String KEY = "PESEPAY";

    private final PesepayClient pesepayClient;

    public PesepayPaymentProvider(PesepayClient pesepayClient) {
        this.pesepayClient = pesepayClient;
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public String refund(Payment payment, BigDecimal amount, String reason) {
        if (payment.getProviderRef() == null || payment.getProviderRef().isBlank()) {
            throw new ApiException("Payment does not have a provider reference to refund");
        }
        return pesepayClient.refund(payment.getProviderRef(), amount.doubleValue(), payment.getCurrency(), reason);
    }
}
