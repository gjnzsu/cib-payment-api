package com.cib.payment.api.application.service;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.Pacs009FiPaymentParser;
import com.cib.payment.api.domain.model.FiParty;
import com.cib.payment.api.domain.model.FiPaymentCandidate;
import com.cib.payment.api.domain.model.FiPaymentIdentifiers;
import com.cib.payment.api.domain.model.Money;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class FiPaymentAdmissionService {
    private final Pacs009FiPaymentParser pacs009Parser;

    public FiPaymentAdmissionService(Pacs009FiPaymentParser pacs009Parser) {
        this.pacs009Parser = pacs009Parser;
    }

    public FiPaymentCandidate admit(String rawXml, String contentType) {
        if (!isSupportedXmlContentType(contentType)) {
            throw new ValidationFailureException("FI payment admission requires supported pacs.009 XML content type");
        }
        if (!hasText(rawXml)) {
            throw new ValidationFailureException("FI payment XML body is required");
        }

        var parsed = pacs009Parser.parse(rawXml);
        requireText(parsed.messageId(), "Message ID is required");
        requireText(parsed.instructionId(), "Instruction ID is required");
        requireText(parsed.endToEndId(), "End-to-end payment reference is required");
        requireText(parsed.amount(), "Settlement amount is required");
        requireText(parsed.currency(), "Settlement currency is required");
        requireText(parsed.settlementDate(), "Settlement date is required");
        requireText(parsed.instructingAgentBic(), "Instructing agent BIC is required");
        requireText(parsed.instructedAgentBic(), "Instructed agent BIC is required");
        if (!"USD".equals(parsed.currency())) {
            throw new ValidationFailureException("Only USD FI settlement currency is supported");
        }

        return new FiPaymentCandidate(
                new FiPaymentIdentifiers(parsed.messageId(), parsed.instructionId(), parsed.endToEndId()),
                new FiParty(parsed.instructingAgentBic()),
                new FiParty(parsed.instructedAgentBic()),
                Optional.ofNullable(parsed.intermediaryAgentBic()).filter(this::hasText),
                new Money(parsed.currency(), parsed.amount()),
                parsed.currency(),
                settlementDate(parsed.settlementDate()),
                parsed.sourceMessageType());
    }

    private LocalDate settlementDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new ValidationFailureException("Settlement date must be an ISO date");
        }
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new ValidationFailureException(message);
        }
    }

    private boolean isSupportedXmlContentType(String contentType) {
        if (!hasText(contentType)) {
            return false;
        }
        var normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.startsWith("application/pacs.009+xml")
                || normalized.startsWith("application/xml")
                || normalized.startsWith("text/xml");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
