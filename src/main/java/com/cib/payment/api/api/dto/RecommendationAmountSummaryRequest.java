package com.cib.payment.api.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@JsonIgnoreProperties(ignoreUnknown = false)
public record RecommendationAmountSummaryRequest(
        @NotBlank @Pattern(regexp = "USD|[A-Z]{3}") String currency,
        @NotBlank @DecimalMin(value = "0.00", inclusive = false) @Pattern(regexp = "\\d+(\\.\\d{1,2})?") String totalAmount,
        @DecimalMin(value = "0.00", inclusive = false) @Pattern(regexp = "\\d+(\\.\\d{1,2})?") String maxSingleAmount) {}
