package com.cib.payment.api.domain.model;

import java.util.Objects;
import java.util.Optional;

public record CollectionEntry(
        CollectionEntryId entryId,
        String entryReference,
        String payerName,
        AccountReference payerAccount,
        Money amount,
        PaymentReason purpose,
        CollectionEntryStatus status,
        Optional<PaymentReason> reason) {
    public CollectionEntry {
        Objects.requireNonNull(entryId, "entryId must not be null");
        Objects.requireNonNull(entryReference, "entryReference must not be null");
        Objects.requireNonNull(payerName, "payerName must not be null");
        Objects.requireNonNull(payerAccount, "payerAccount must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(status, "status must not be null");
        reason = reason == null ? Optional.empty() : reason;
    }
}
