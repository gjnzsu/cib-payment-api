package com.cib.payment.api.domain.model;

import java.util.List;

public record AdvisorSimulationPlan(
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
        boolean requiresUserConfirmation) {
    public AdvisorSimulationPlan {
        requireText(method, "method must not be blank");
        requireText(endpoint, "endpoint must not be blank");
        requiredScopes = List.copyOf(requiredScopes == null ? List.of() : requiredScopes);
        requiredHeaders = List.copyOf(requiredHeaders == null ? List.of() : requiredHeaders);
        requireText(payloadFormat, "payloadFormat must not be blank");
        requireText(mockScenario, "mockScenario must not be blank");
        requireText(statusEndpointTemplate, "statusEndpointTemplate must not be blank");
        requireText(expectedStatus, "expectedStatus must not be blank");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
