package com.cib.payment.api.domain.model;

public record FiPaymentIdentifiers(
        String messageId,
        String instructionId,
        String originalPaymentReference) {
}
