package com.cib.payment.api.api.dto;

import java.time.Instant;
import java.time.LocalDate;

public record RtgsPaymentStatusResponse(
        String paymentId,
        String rail,
        String paymentReference,
        String clientSegment,
        AccountReferenceRequest debtorAccount,
        AccountReferenceRequest creditorAccount,
        String instructingAgentBic,
        String instructedAgentBic,
        MoneyRequest amount,
        LocalDate requestedSettlementDate,
        String settlementPriority,
        String purpose,
        String status,
        boolean settlementFinality,
        Instant createdAt,
        Instant updatedAt,
        String correlationId,
        PaymentReasonResponse reason,
        PaymentLinksResponse links) {}
