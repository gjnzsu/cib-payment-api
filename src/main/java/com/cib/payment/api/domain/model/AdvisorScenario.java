package com.cib.payment.api.domain.model;

import java.util.Objects;

public record AdvisorScenario(
        AdvisorScenarioId scenarioId,
        String businessLabel,
        String businessDescription,
        boolean simulatorOnly,
        boolean requiresUserConfirmation,
        RecommendationIntent recommendationIntent,
        AdvisorRecommendationSummary recommendation,
        AdvisorSimulationPlan simulationPlan,
        AdvisorFeedbackReport feedbackReport) {
    public AdvisorScenario {
        Objects.requireNonNull(scenarioId, "scenarioId must not be null");
        requireText(businessLabel, "businessLabel must not be blank");
        requireText(businessDescription, "businessDescription must not be blank");
        Objects.requireNonNull(recommendationIntent, "recommendationIntent must not be null");
        Objects.requireNonNull(recommendation, "recommendation must not be null");
        Objects.requireNonNull(simulationPlan, "simulationPlan must not be null");
        Objects.requireNonNull(feedbackReport, "feedbackReport must not be null");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
