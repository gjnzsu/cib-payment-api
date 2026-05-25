package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.Money;

public interface Pain001PaymentInitiationParser {
    ParsedInitiation parse(String xml);

    record ParsedInitiation(
            String messageId,
            String paymentInformationId,
            AccountReference debtor,
            String creditorName,
            String creditorAccount,
            String creditorProxyId,
            String creditorProxyType,
            String creditorParticipantIdentifier,
            Money amount,
            String endToEndId,
            String instructionId,
            String remittanceInformation,
            String structuredCreditorReference,
            String purposeCode,
            String categoryPurposeCode,
            String sourceMessageType) {
    }
}
