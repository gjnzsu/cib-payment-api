package com.cib.payment.api.api.dto;

public record RecommendationOptionResponse(
        String rail,
        String arrangement,
        String clientSegment,
        String reasonCode) {}
