package com.cib.payment.api.domain.model;

import java.util.List;

public record RecommendationNextApiGuidance(
        String method,
        String endpoint,
        List<String> requiredScopes,
        List<String> requiredHeaders,
        String payloadFormat) {
    public RecommendationNextApiGuidance {
        requireText(method, "method must not be blank");
        requireText(endpoint, "endpoint must not be blank");
        requiredScopes = List.copyOf(requiredScopes == null ? List.of() : requiredScopes);
        requiredHeaders = List.copyOf(requiredHeaders == null ? List.of() : requiredHeaders);
        requireText(payloadFormat, "payloadFormat must not be blank");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
