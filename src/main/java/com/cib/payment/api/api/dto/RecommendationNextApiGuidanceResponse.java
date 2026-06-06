package com.cib.payment.api.api.dto;

import java.util.List;

public record RecommendationNextApiGuidanceResponse(
        String method,
        String endpoint,
        List<String> requiredScopes,
        List<String> requiredHeaders,
        String payloadFormat) {
    public RecommendationNextApiGuidanceResponse {
        requiredScopes = List.copyOf(requiredScopes == null ? List.of() : requiredScopes);
        requiredHeaders = List.copyOf(requiredHeaders == null ? List.of() : requiredHeaders);
    }
}
