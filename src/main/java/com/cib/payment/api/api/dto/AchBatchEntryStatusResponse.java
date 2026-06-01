package com.cib.payment.api.api.dto;

public record AchBatchEntryStatusResponse(
        String entryId,
        String entryReference,
        String receiverName,
        MoneyRequest amount,
        String status,
        PaymentReasonResponse reason) {}
