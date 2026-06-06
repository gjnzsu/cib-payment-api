package com.cib.payment.api.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreatePaymentRailRecommendationRequest(
        @NotBlank @Pattern(regexp = "CORPORATE|FI") String clientSegment,
        String paymentPurpose,
        @Min(1) int paymentCount,
        @Valid @NotNull RecommendationAmountSummaryRequest amountSummary,
        @NotBlank String debtorCountry,
        @NotBlank String creditorCountry,
        @NotBlank @Pattern(regexp = "IMMEDIATE|SAME_DAY|NEXT_DAY|STANDARD") String urgency,
        String creditorType,
        Boolean requiresFinality,
        Boolean batchPreferred,
        @Pattern(regexp = "LOW|MEDIUM|HIGH") String costSensitivity,
        Boolean fiToFi,
        @Pattern(regexp = "DOMESTIC_REAL_TIME_CLEARING|BATCH_CLEARING_NET_SETTLEMENT|DOMESTIC_INTERBANK_GROSS_SETTLEMENT|CORRESPONDENT_ACCOUNT_PATH")
                String arrangementPreference,
        @Valid RecommendationDebtorAccountProfileRequest debtorAccountProfile) {

    public int debtorAccountCount() {
        return debtorAccountProfile == null ? 1 : debtorAccountProfile.count();
    }

    public boolean requiresFinalityValue() {
        return Boolean.TRUE.equals(requiresFinality);
    }

    public boolean batchPreferredValue() {
        return Boolean.TRUE.equals(batchPreferred);
    }

    public boolean fiToFiValue() {
        return Boolean.TRUE.equals(fiToFi);
    }
}
