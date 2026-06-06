package com.cib.payment.api.api.dto;

public record RecommendationTradeoffResponse(
        String rail,
        String arrangement,
        String speed,
        String cost,
        String finality,
        String intentFit,
        String intentFitReason,
        String summary) {}
