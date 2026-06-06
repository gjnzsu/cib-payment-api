package com.cib.payment.api.domain.model;

import java.util.Objects;

public record RecommendationTradeoff(
        PaymentRail rail,
        PaymentArrangement arrangement,
        RecommendationSpeed speed,
        RecommendationCost cost,
        RecommendationFinality finality,
        RecommendationIntentFit intentFit,
        String intentFitReason,
        String summary) {
    public RecommendationTradeoff {
        Objects.requireNonNull(rail, "rail must not be null");
        Objects.requireNonNull(arrangement, "arrangement must not be null");
        Objects.requireNonNull(speed, "speed must not be null");
        Objects.requireNonNull(cost, "cost must not be null");
        Objects.requireNonNull(finality, "finality must not be null");
        Objects.requireNonNull(intentFit, "intentFit must not be null");
        requireText(intentFitReason, "intentFitReason must not be blank");
        requireText(summary, "summary must not be blank");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
