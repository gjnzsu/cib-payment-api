package com.cib.payment.api.api.dto;

public record AdvisorScenarioDetailResponse(
        String scenarioId,
        String businessLabel,
        String businessDescription,
        boolean simulatorOnly,
        boolean requiresUserConfirmation,
        AdvisorRecommendationIntentResponse recommendationIntent,
        AdvisorRecommendationSummaryResponse recommendation,
        AdvisorSimulationPlanResponse simulationPlan,
        AdvisorFeedbackReportResponse feedbackReport,
        String correlationId) {}
