package com.cib.payment.api.domain.model;

public record RecommendationWarning(String code, String message) {
    public RecommendationWarning {
        requireText(code, "code must not be blank");
        requireText(message, "message must not be blank");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
