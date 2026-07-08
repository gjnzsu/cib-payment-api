package com.cib.payment.api.domain.model;

public record AdvisorFeedbackReport(
        String validationStatus,
        String expectedOutcome,
        String businessConclusion,
        String nextStep) {
    public AdvisorFeedbackReport {
        requireText(validationStatus, "validationStatus must not be blank");
        requireText(expectedOutcome, "expectedOutcome must not be blank");
        requireText(businessConclusion, "businessConclusion must not be blank");
        requireText(nextStep, "nextStep must not be blank");
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
