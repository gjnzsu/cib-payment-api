package com.cib.payment.api.domain.model;

public record IsoPaymentCandidate(
        AccountReference debtor,
        BeneficiaryIdentifier beneficiary,
        Money amount,
        String endToEndId,
        String instructionId,
        String paymentReference,
        String remittanceInformation,
        String purposeCode,
        String categoryPurposeCode,
        String messageId,
        String paymentInformationId,
        String sourceMessageType) {
}
