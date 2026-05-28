package com.cib.payment.api.application.port;

public interface Pacs009FiPaymentParser {
    ParsedFiPayment parse(String xml);

    record ParsedFiPayment(
            String messageId,
            String instructionId,
            String endToEndId,
            String amount,
            String currency,
            String settlementDate,
            String instructingAgentBic,
            String instructedAgentBic,
            String intermediaryAgentBic,
            String sourceMessageType) {
    }
}
