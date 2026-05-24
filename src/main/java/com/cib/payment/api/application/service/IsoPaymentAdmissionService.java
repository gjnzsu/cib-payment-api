package com.cib.payment.api.application.service;

import com.cib.payment.api.application.exception.SemanticPaymentException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.Pain001PaymentInitiationParser;
import com.cib.payment.api.domain.model.BeneficiaryIdentifier;
import com.cib.payment.api.domain.model.IsoPaymentCandidate;
import java.util.Locale;

public class IsoPaymentAdmissionService {
    private final Pain001PaymentInitiationParser pain001Parser;

    public IsoPaymentAdmissionService(Pain001PaymentInitiationParser pain001Parser) {
        this.pain001Parser = pain001Parser;
    }

    public IsoPaymentCandidate admit(String rawXml, String contentType) {
        if (!isSupportedXmlContentType(contentType)) {
            throw new ValidationFailureException("Payment initiation requires supported pain.001 XML content type");
        }

        var parsed = pain001Parser.parse(rawXml);
        if (!"HKD".equals(parsed.amount().currency())) {
            throw new SemanticPaymentException("Only HKD payments are supported for HK ISO payment simulation");
        }
        var beneficiary = beneficiaryIdentifier(parsed);
        if (!beneficiary.hasAccountOrProxy()) {
            throw new ValidationFailureException("Beneficiary account or FPS proxy is required");
        }
        if (!hasText(parsed.endToEndId()) && !hasText(parsed.structuredCreditorReference())) {
            throw new ValidationFailureException("EndToEndId or payment reference is required");
        }

        return new IsoPaymentCandidate(
                parsed.debtor(),
                beneficiary,
                parsed.amount(),
                parsed.endToEndId(),
                parsed.instructionId(),
                parsed.structuredCreditorReference(),
                parsed.remittanceInformation(),
                parsed.purposeCode(),
                parsed.categoryPurposeCode(),
                parsed.messageId(),
                parsed.paymentInformationId(),
                parsed.sourceMessageType());
    }

    private BeneficiaryIdentifier beneficiaryIdentifier(Pain001PaymentInitiationParser.ParsedInitiation parsed) {
        return BeneficiaryIdentifier.of(
                parsed.creditorAccount(),
                parsed.creditorName(),
                parsed.creditorParticipantIdentifier(),
                parsed.creditorProxyType(),
                parsed.creditorProxyId());
    }

    private boolean isSupportedXmlContentType(String contentType) {
        if (!hasText(contentType)) {
            return false;
        }
        var normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("application/xml")
                || normalized.startsWith("text/xml")
                || normalized.startsWith("application/pain.001+xml");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
