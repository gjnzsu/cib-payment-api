package com.cib.payment.api.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateMandateRequest(
        @NotBlank String mandateProfile,
        String mandateReference,
        @NotBlank String creditorName,
        @NotBlank String debtorName,
        @Valid @NotNull AccountReferenceRequest creditorAccount,
        @Valid @NotNull AccountReferenceRequest debtorAccount,
        @Valid @NotNull MoneyRequest maximumAmount,
        @NotBlank String frequency,
        @NotBlank String purpose) {}
