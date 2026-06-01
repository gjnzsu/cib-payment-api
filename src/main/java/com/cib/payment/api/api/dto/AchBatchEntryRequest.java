package com.cib.payment.api.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = false)
public record AchBatchEntryRequest(
        @NotBlank String entryReference,
        @NotBlank String receiverName,
        @Valid @NotNull AccountReferenceRequest receiverAccount,
        @Valid @NotNull MoneyRequest amount,
        @NotBlank String purpose) {}
