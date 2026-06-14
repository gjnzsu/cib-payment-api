package com.cib.payment.api.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PaymentRailRecommendationResponse(
        String recommendationId,
        String recommendationStatus,
        RecommendationOptionResponse recommendedOption,
        String confidenceLevel,
        String decisionSummary,
        List<String> matchedFactors,
        List<RecommendationWarningResponse> warnings,
        List<RecommendationOptionResponse> alternatives,
        List<RecommendationTradeoffResponse> tradeoffs,
        RecommendationNextApiGuidanceResponse nextApiGuidance,
        String correlationId) {
    public PaymentRailRecommendationResponse {
        matchedFactors = List.copyOf(matchedFactors == null ? List.of() : matchedFactors);
        warnings = List.copyOf(warnings == null ? List.of() : warnings);
        alternatives = List.copyOf(alternatives == null ? List.of() : alternatives);
        tradeoffs = List.copyOf(tradeoffs == null ? List.of() : tradeoffs);
    }
}
