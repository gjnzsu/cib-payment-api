package com.cib.payment.api.api.dto;

import com.cib.payment.api.api.validation.DecimalString;
import jakarta.validation.constraints.NotBlank;

public record MoneyRequest(
        @NotBlank String currency,
        @NotBlank @DecimalString String value) {}
