package com.cib.payment.api.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CollectionRecord(
        CollectionId collectionId,
        String clientId,
        CollectionProfile collectionProfile,
        String collectionReference,
        String mandateReference,
        String creditorName,
        String debtorName,
        AccountReference settlementAccount,
        String payerBankCode,
        String payerAlias,
        Money amount,
        PaymentReason purpose,
        List<CollectionEntry> entries,
        CollectionStatus status,
        CorrelationId correlationId,
        Instant createdAt,
        Instant updatedAt,
        Optional<PaymentReason> reason) {
    public CollectionRecord {
        Objects.requireNonNull(collectionId, "collectionId must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(collectionProfile, "collectionProfile must not be null");
        Objects.requireNonNull(collectionReference, "collectionReference must not be null");
        Objects.requireNonNull(mandateReference, "mandateReference must not be null");
        Objects.requireNonNull(creditorName, "creditorName must not be null");
        Objects.requireNonNull(debtorName, "debtorName must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries must not be null"));
        reason = reason == null ? Optional.empty() : reason;
    }

    public int entryCount() {
        return entries.size();
    }

    public Money totalAmount() {
        if (amount != null) {
            return amount;
        }
        var currency = entries.stream()
                .findFirst()
                .map(entry -> entry.amount().currency())
                .orElse("USD");
        var total = entries.stream()
                .map(entry -> new BigDecimal(entry.amount().value()))
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new Money(currency, total.toPlainString());
    }
}
