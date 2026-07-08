package com.cib.payment.api.api.dto;

import java.util.List;

public record AdvisorSimulationPlanResponse(
        String method,
        String endpoint,
        List<String> requiredScopes,
        List<String> requiredHeaders,
        boolean idempotencyRequired,
        String payloadFormat,
        String mockScenario,
        String statusEndpointTemplate,
        String expectedStatus,
        boolean simulatorOnly,
        boolean requiresUserConfirmation) {}
