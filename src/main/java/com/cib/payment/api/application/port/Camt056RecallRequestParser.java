package com.cib.payment.api.application.port;

public interface Camt056RecallRequestParser {
    ParsedRecallRequest parse(String xml);

    record ParsedRecallRequest(
            String messageId,
            String caseId,
            String originalPaymentReference,
            String reasonCode,
            String sourceMessageType) {}
}
