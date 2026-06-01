package com.cib.payment.api.api.dto;

import java.time.Instant;
import java.util.List;

public record AchBatchResponse(
        String batchId,
        String rail,
        String status,
        int entryCount,
        MoneyRequest totalAmount,
        List<AchBatchEntryStatusResponse> entries,
        Instant createdAt,
        Instant updatedAt,
        String correlationId,
        PaymentReasonResponse reason,
        PaymentLinksResponse links) {}
