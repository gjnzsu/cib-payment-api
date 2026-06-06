package com.cib.payment.api.domain.model;

import java.util.Objects;

public record RecommendationOption(
        PaymentRail rail,
        PaymentArrangement arrangement,
        RecommendationClientSegment clientSegment,
        String reasonCode) {
    public RecommendationOption {
        Objects.requireNonNull(rail, "rail must not be null");
        Objects.requireNonNull(arrangement, "arrangement must not be null");
        Objects.requireNonNull(clientSegment, "clientSegment must not be null");
        requireText(reasonCode, "reasonCode must not be blank");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
