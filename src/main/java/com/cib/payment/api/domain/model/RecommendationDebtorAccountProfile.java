package com.cib.payment.api.domain.model;

public record RecommendationDebtorAccountProfile(int count) {
    public RecommendationDebtorAccountProfile {
        if (count < 1) {
            throw new IllegalArgumentException("count must be at least 1");
        }
    }

    public static RecommendationDebtorAccountProfile defaultProfile() {
        return new RecommendationDebtorAccountProfile(1);
    }
}
