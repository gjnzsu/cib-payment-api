package com.cib.payment.api.api.dto;

public record AdvisorRecommendationIntentResponse(
        String clientSegment,
        String paymentPurpose,
        int paymentCount,
        AdvisorAmountSummaryResponse amountSummary,
        String debtorCountry,
        String creditorCountry,
        String urgency,
        String creditorType,
        boolean requiresFinality,
        boolean batchPreferred,
        String costSensitivity,
        boolean fiToFi,
        String arrangementPreference) {}
