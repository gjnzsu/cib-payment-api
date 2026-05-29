package com.cib.payment.api.api.dto;

public record FiPaymentAcknowledgementResponse(
        String paymentId,
        String status,
        CorrespondentSettlementContextResponse correspondentSettlementContext,
        String correlationId,
        PaymentReasonResponse reason,
        String statusLink) {
}
