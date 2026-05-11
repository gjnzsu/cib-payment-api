package com.cib.payment.api.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateDomesticPaymentRequest(
        @Valid @NotNull AccountReferenceRequest debtorAccount,
        @Valid @NotNull AccountReferenceRequest creditorAccount,
        @Valid @NotNull MoneyRequest amount,
        @NotBlank String paymentReference,
        String remittanceInformation,
        LocalDate requestedExecutionDate) {}
