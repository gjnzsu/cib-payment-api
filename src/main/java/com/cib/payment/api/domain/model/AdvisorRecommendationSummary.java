package com.cib.payment.api.domain.model;

import java.util.List;
import java.util.Objects;

public record AdvisorRecommendationSummary(
        PaymentRail rail,
        PaymentArrangement arrangement,
        RecommendationClientSegment clientSegment,
        RecommendationConfidence confidenceLevel,
        String reasonCode,
        String summary,
        List<String> matchedFactors,
        List<String> tradeoffs) {
    public AdvisorRecommendationSummary {
        Objects.requireNonNull(rail, "rail must not be null");
        Objects.requireNonNull(arrangement, "arrangement must not be null");
        Objects.requireNonNull(clientSegment, "clientSegment must not be null");
        Objects.requireNonNull(confidenceLevel, "confidenceLevel must not be null");
        requireText(reasonCode, "reasonCode must not be blank");
        requireText(summary, "summary must not be blank");
        matchedFactors = List.copyOf(matchedFactors == null ? List.of() : matchedFactors);
        tradeoffs = List.copyOf(tradeoffs == null ? List.of() : tradeoffs);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
