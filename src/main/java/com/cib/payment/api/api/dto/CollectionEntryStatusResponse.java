package com.cib.payment.api.api.dto;

public record CollectionEntryStatusResponse(
        String entryId,
        String entryReference,
        String payerName,
        MoneyRequest amount,
        String status,
        PaymentReasonResponse reason) {}
