package com.cib.payment.api.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record MandateRecord(
        MandateId mandateId,
        String clientId,
        MandateProfile mandateProfile,
        String mandateReference,
        String creditorName,
        String debtorName,
        AccountReference creditorAccount,
        AccountReference debtorAccount,
        Money maximumAmount,
        String frequency,
        PaymentReason purpose,
        MandateStatus status,
        CorrelationId correlationId,
        Instant createdAt,
        Instant updatedAt,
        Optional<PaymentReason> reason) {
    public MandateRecord {
        Objects.requireNonNull(mandateId, "mandateId must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(mandateProfile, "mandateProfile must not be null");
        Objects.requireNonNull(mandateReference, "mandateReference must not be null");
        Objects.requireNonNull(creditorName, "creditorName must not be null");
        Objects.requireNonNull(debtorName, "debtorName must not be null");
        Objects.requireNonNull(creditorAccount, "creditorAccount must not be null");
        Objects.requireNonNull(debtorAccount, "debtorAccount must not be null");
        Objects.requireNonNull(maximumAmount, "maximumAmount must not be null");
        Objects.requireNonNull(frequency, "frequency must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        reason = reason == null ? Optional.empty() : reason;
    }

    public MandateRecord withStatus(MandateStatus newStatus, Instant now, PaymentReason newReason) {
        return new MandateRecord(
                mandateId,
                clientId,
                mandateProfile,
                mandateReference,
                creditorName,
                debtorName,
                creditorAccount,
                debtorAccount,
                maximumAmount,
                frequency,
                purpose,
                newStatus,
                correlationId,
                createdAt,
                now,
                Optional.ofNullable(newReason));
    }
}
