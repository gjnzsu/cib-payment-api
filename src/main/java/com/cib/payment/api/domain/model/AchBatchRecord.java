package com.cib.payment.api.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AchBatchRecord(
        AchBatchId batchId,
        String clientId,
        String batchReference,
        String originatorName,
        LocalDate effectiveEntryDate,
        AccountReference settlementAccount,
        List<AchBatchEntry> entries,
        AchBatchStatus status,
        CorrelationId correlationId,
        Instant createdAt,
        Instant updatedAt,
        Optional<PaymentReason> reason) {
    public AchBatchRecord {
        Objects.requireNonNull(batchId, "batchId must not be null");
        Objects.requireNonNull(clientId, "clientId must not be null");
        Objects.requireNonNull(batchReference, "batchReference must not be null");
        Objects.requireNonNull(originatorName, "originatorName must not be null");
        Objects.requireNonNull(effectiveEntryDate, "effectiveEntryDate must not be null");
        Objects.requireNonNull(settlementAccount, "settlementAccount must not be null");
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
