package com.cib.payment.api.api.dto;

public record AdvisorAmountSummaryResponse(
        String currency,
        String totalAmount,
        String maxSingleAmount) {}
