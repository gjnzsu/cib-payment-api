package com.cib.payment.api.domain.model;

import java.util.Objects;

public record RecommendationIntent(
        RecommendationClientSegment clientSegment,
        String paymentPurpose,
        int paymentCount,
        RecommendationAmountSummary amountSummary,
        String debtorCountry,
        String creditorCountry,
        RecommendationUrgency urgency,
        String creditorType,
        boolean requiresFinality,
        boolean batchPreferred,
        RecommendationCostSensitivity costSensitivity,
        boolean fiToFi,
        PaymentArrangement arrangementPreference,
        RecommendationDebtorAccountProfile debtorAccountProfile,
        CorrelationId correlationId) {
    public RecommendationIntent {
        Objects.requireNonNull(clientSegment, "clientSegment must not be null");
        if (paymentCount < 1) {
            throw new IllegalArgumentException("paymentCount must be at least 1");
        }
        Objects.requireNonNull(amountSummary, "amountSummary must not be null");
        requireText(debtorCountry, "debtorCountry must not be blank");
        requireText(creditorCountry, "creditorCountry must not be blank");
        Objects.requireNonNull(urgency, "urgency must not be null");
        costSensitivity = costSensitivity == null ? RecommendationCostSensitivity.MEDIUM : costSensitivity;
        debtorAccountProfile = debtorAccountProfile == null
                ? RecommendationDebtorAccountProfile.defaultProfile()
                : debtorAccountProfile;
        Objects.requireNonNull(correlationId, "correlationId must not be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private RecommendationClientSegment clientSegment;
        private String paymentPurpose;
        private int paymentCount;
        private RecommendationAmountSummary amountSummary;
        private String debtorCountry;
        private String creditorCountry;
        private RecommendationUrgency urgency;
        private String creditorType;
        private boolean requiresFinality;
        private boolean batchPreferred;
        private RecommendationCostSensitivity costSensitivity;
        private boolean fiToFi;
        private PaymentArrangement arrangementPreference;
        private RecommendationDebtorAccountProfile debtorAccountProfile;
        private CorrelationId correlationId;

        private Builder() {}

        public Builder clientSegment(RecommendationClientSegment clientSegment) {
            this.clientSegment = clientSegment;
            return this;
        }

        public Builder paymentPurpose(String paymentPurpose) {
            this.paymentPurpose = paymentPurpose;
            return this;
        }

        public Builder paymentCount(int paymentCount) {
            this.paymentCount = paymentCount;
            return this;
        }

        public Builder amountSummary(RecommendationAmountSummary amountSummary) {
            this.amountSummary = amountSummary;
            return this;
        }

        public Builder debtorCountry(String debtorCountry) {
            this.debtorCountry = debtorCountry;
            return this;
        }

        public Builder creditorCountry(String creditorCountry) {
            this.creditorCountry = creditorCountry;
            return this;
        }

        public Builder urgency(RecommendationUrgency urgency) {
            this.urgency = urgency;
            return this;
        }

        public Builder creditorType(String creditorType) {
            this.creditorType = creditorType;
            return this;
        }

        public Builder requiresFinality(boolean requiresFinality) {
            this.requiresFinality = requiresFinality;
            return this;
        }

        public Builder batchPreferred(boolean batchPreferred) {
            this.batchPreferred = batchPreferred;
            return this;
        }

        public Builder costSensitivity(RecommendationCostSensitivity costSensitivity) {
            this.costSensitivity = costSensitivity;
            return this;
        }

        public Builder fiToFi(boolean fiToFi) {
            this.fiToFi = fiToFi;
            return this;
        }

        public Builder arrangementPreference(PaymentArrangement arrangementPreference) {
            this.arrangementPreference = arrangementPreference;
            return this;
        }

        public Builder debtorAccountProfile(RecommendationDebtorAccountProfile debtorAccountProfile) {
            this.debtorAccountProfile = debtorAccountProfile;
            return this;
        }

        public Builder correlationId(CorrelationId correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public RecommendationIntent build() {
            return new RecommendationIntent(
                    clientSegment,
                    paymentPurpose,
                    paymentCount,
                    amountSummary,
                    debtorCountry,
                    creditorCountry,
                    urgency,
                    creditorType,
                    requiresFinality,
                    batchPreferred,
                    costSensitivity,
                    fiToFi,
                    arrangementPreference,
                    debtorAccountProfile,
                    correlationId);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
