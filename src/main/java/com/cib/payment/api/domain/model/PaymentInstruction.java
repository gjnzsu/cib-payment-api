package com.cib.payment.api.domain.model;

import java.time.LocalDate;

public record PaymentInstruction(
        AccountReference debtorAccount,
        AccountReference creditorAccount,
        Money amount,
        String paymentReference,
        String remittanceInformation,
        LocalDate requestedExecutionDate
) {
}
