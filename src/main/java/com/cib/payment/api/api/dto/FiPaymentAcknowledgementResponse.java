package com.cib.payment.api.api.dto;

import com.cib.payment.api.domain.model.FiPaymentId;

public record FiPaymentAcknowledgementResponse(
        FiPaymentId paymentId,
        String status,
        CorrespondentSettlementContextResponse correspondentSettlementContext,
        String correlationId,
        PaymentReasonResponse reason,
        String statusLink) {
}
