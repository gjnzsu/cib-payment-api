package com.cib.payment.api.api.dto;

public record AdvisorScenarioSummaryResponse(
        String scenarioId,
        String businessLabel,
        String businessDescription,
        String recommendedRail,
        String recommendedArrangement,
        boolean simulatorOnly,
        boolean requiresUserConfirmation) {}
