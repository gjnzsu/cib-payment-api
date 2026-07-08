package com.cib.payment.api.api.dto;

public record AdvisorFeedbackReportResponse(
        String validationStatus,
        String expectedOutcome,
        String businessConclusion,
        String nextStep) {}
