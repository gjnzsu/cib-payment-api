package com.cib.payment.api.api.dto;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        int status,
        String correlationId,
        List<ValidationErrorDetailResponse> details) {}
