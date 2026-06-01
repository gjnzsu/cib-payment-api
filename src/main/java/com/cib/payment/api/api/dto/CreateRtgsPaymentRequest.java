package com.cib.payment.api.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateRtgsPaymentRequest(
        @NotBlank String paymentReference,
        @NotBlank @Pattern(regexp = "CORPORATE|FI") String clientSegment,
        @Valid AccountReferenceRequest debtorAccount,
        @Valid AccountReferenceRequest creditorAccount,
        String instructingAgentBic,
        String instructedAgentBic,
        @Valid @NotNull MoneyRequest amount,
        @NotNull LocalDate requestedSettlementDate,
        @NotBlank String settlementPriority,
        @NotBlank String purpose) {

    public boolean isCorporateSegment() {
        return "CORPORATE".equals(clientSegment);
    }

    public boolean isFiSegment() {
        return "FI".equals(clientSegment);
    }

    public boolean hasRequiredCorporateParties() {
        return debtorAccount != null && creditorAccount != null;
    }

    public boolean hasRequiredFiAgents() {
        return hasText(instructingAgentBic) && hasText(instructedAgentBic);
    }

    public boolean hasUsdAmount() {
        return amount != null && "USD".equals(amount.currency());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
