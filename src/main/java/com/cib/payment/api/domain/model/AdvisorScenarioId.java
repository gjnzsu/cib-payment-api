package com.cib.payment.api.domain.model;

public record AdvisorScenarioId(String value) {
    public AdvisorScenarioId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("advisor scenario id must not be blank");
        }
    }
}
