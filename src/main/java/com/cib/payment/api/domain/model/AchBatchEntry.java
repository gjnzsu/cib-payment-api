package com.cib.payment.api.domain.model;

import java.util.Objects;
import java.util.Optional;

public record AchBatchEntry(
        AchEntryId entryId,
        String entryReference,
        String receiverName,
        AccountReference receiverAccount,
        Money amount,
        PaymentReason purpose,
        AchEntryStatus status,
        Optional<PaymentReason> reason) {
    public AchBatchEntry {
        Objects.requireNonNull(entryId, "entryId must not be null");
        Objects.requireNonNull(entryReference, "entryReference must not be null");
        Objects.requireNonNull(receiverName, "receiverName must not be null");
        Objects.requireNonNull(receiverAccount, "receiverAccount must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(status, "status must not be null");
        reason = reason == null ? Optional.empty() : reason;
    }

    public AchBatchEntry(
            AchEntryId entryId,
            String entryReference,
            String receiverName,
            AccountReference receiverAccount,
            Money amount,
            PaymentReason purpose,
            AchEntryStatus status,
            PaymentReason reason) {
        this(entryId, entryReference, receiverName, receiverAccount, amount, purpose, status, Optional.ofNullable(reason));
    }
}
