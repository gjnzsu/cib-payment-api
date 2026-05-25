package com.cib.payment.api.domain.model;

public record InternalInterbankTransfer(
        String internalMessageId,
        PaymentId paymentId,
        AccountReference debtor,
        BeneficiaryIdentifier beneficiary,
        Money amount,
        String endToEndId,
        String instructionId,
        String paymentReference,
        String payerParticipantIdentifier,
        String payeeParticipantIdentifier,
        CorrelationId correlationId) {
}
