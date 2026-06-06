package com.cib.payment.api.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record PaymentRailRecommendation(
        PaymentRailRecommendationId recommendationId,
        RecommendationStatus recommendationStatus,
        Optional<RecommendationOption> recommendedOption,
        RecommendationConfidence confidenceLevel,
        String decisionSummary,
        List<RecommendationMatchedFactor> matchedFactors,
        List<RecommendationWarning> warnings,
        List<RecommendationOption> alternatives,
        List<RecommendationTradeoff> tradeoffs,
        Optional<RecommendationNextApiGuidance> nextApiGuidance,
        CorrelationId correlationId) {
    public PaymentRailRecommendation {
        Objects.requireNonNull(recommendationId, "recommendationId must not be null");
        Objects.requireNonNull(recommendationStatus, "recommendationStatus must not be null");
        recommendedOption = recommendedOption == null ? Optional.empty() : recommendedOption;
        Objects.requireNonNull(confidenceLevel, "confidenceLevel must not be null");
        requireText(decisionSummary, "decisionSummary must not be blank");
        matchedFactors = List.copyOf(matchedFactors == null ? List.of() : matchedFactors);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        alternatives = List.copyOf(alternatives == null ? List.of() : alternatives);
        tradeoffs = List.copyOf(tradeoffs == null ? List.of() : tradeoffs);
        nextApiGuidance = nextApiGuidance == null ? Optional.empty() : nextApiGuidance;
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        if (recommendationStatus == RecommendationStatus.UNSUPPORTED
                && (recommendedOption.isPresent() || nextApiGuidance.isPresent())) {
            throw new IllegalArgumentException("unsupported recommendations must not include option or guidance");
        }
    }

    public static PaymentRailRecommendation unsupported(
            PaymentRailRecommendationId recommendationId,
            String decisionSummary,
            List<RecommendationWarning> warnings,
            CorrelationId correlationId) {
        return new PaymentRailRecommendation(
                recommendationId,
                RecommendationStatus.UNSUPPORTED,
                Optional.empty(),
                RecommendationConfidence.LOW,
                decisionSummary,
                List.of(),
                warnings,
                List.of(),
                List.of(),
                Optional.empty(),
                correlationId);
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
