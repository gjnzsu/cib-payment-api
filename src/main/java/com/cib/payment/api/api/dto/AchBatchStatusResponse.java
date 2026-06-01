package com.cib.payment.api.api.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AchBatchStatusResponse(
        String batchId,
        String rail,
        String batchReference,
        String originatorName,
        LocalDate effectiveEntryDate,
        String status,
        int entryCount,
        MoneyRequest totalAmount,
        List<AchBatchEntryStatusResponse> entries,
        Instant createdAt,
        Instant updatedAt,
        String correlationId,
        PaymentReasonResponse reason,
        PaymentLinksResponse links) {}
