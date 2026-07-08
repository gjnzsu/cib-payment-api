package com.cib.payment.api.api.dto;

import java.time.Instant;
import java.util.List;

public record CollectionResponse(
        String collectionId,
        String collectionProfile,
        String status,
        int entryCount,
        MoneyRequest totalAmount,
        List<CollectionEntryStatusResponse> entries,
        Instant createdAt,
        Instant updatedAt,
        String correlationId,
        PaymentReasonResponse reason,
        PaymentLinksResponse links) {}
