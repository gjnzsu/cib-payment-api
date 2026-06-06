package com.cib.payment.api.domain.model;

import java.math.BigDecimal;
import java.util.Optional;

public record RecommendationAmountSummary(
        String currency,
        BigDecimal totalAmount,
        Optional<BigDecimal> maxSingleAmount) {
    public RecommendationAmountSummary(String currency, String totalAmount, Optional<String> maxSingleAmount) {
        this(
                currency,
                totalAmount == null ? null : new BigDecimal(totalAmount),
                maxSingleAmount == null ? Optional.empty() : maxSingleAmount.map(BigDecimal::new));
    }

    public RecommendationAmountSummary {
        requireText(currency, "currency must not be blank");
        if (totalAmount == null || totalAmount.signum() <= 0) {
            throw new IllegalArgumentException("totalAmount must be positive");
        }
        maxSingleAmount = maxSingleAmount == null ? Optional.empty() : maxSingleAmount;
        maxSingleAmount.ifPresent(value -> {
            if (value.signum() <= 0) {
                throw new IllegalArgumentException("maxSingleAmount must be positive");
            }
        });
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
