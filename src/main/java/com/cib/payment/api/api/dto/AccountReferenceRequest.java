package com.cib.payment.api.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountReferenceRequest(
        @NotBlank String bankCode,
        @NotBlank String accountNumber,
        @NotBlank String accountName) {}
