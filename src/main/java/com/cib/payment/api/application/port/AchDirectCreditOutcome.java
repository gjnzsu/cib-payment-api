package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.AchBatchStatus;
import com.cib.payment.api.domain.model.AchEntryId;
import com.cib.payment.api.domain.model.AchEntryStatus;
import com.cib.payment.api.domain.model.PaymentReason;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record AchDirectCreditOutcome(
        AchBatchStatus batchStatus,
        List<EntryOutcome> entryOutcomes,
        Optional<PaymentReason> reason) {
    public AchDirectCreditOutcome {
        Objects.requireNonNull(batchStatus, "batchStatus must not be null");
        entryOutcomes = List.copyOf(Objects.requireNonNull(entryOutcomes, "entryOutcomes must not be null"));
        reason = reason == null ? Optional.empty() : reason;
    }

    public AchDirectCreditOutcome(
            AchBatchStatus batchStatus,
            List<EntryOutcome> entryOutcomes,
            PaymentReason reason) {
        this(batchStatus, entryOutcomes, Optional.ofNullable(reason));
    }

    public record EntryOutcome(
            AchEntryId entryId,
            String entryReference,
            AchEntryStatus status,
            Optional<PaymentReason> reason) {
        public EntryOutcome {
            Objects.requireNonNull(entryId, "entryId must not be null");
            Objects.requireNonNull(entryReference, "entryReference must not be null");
            Objects.requireNonNull(status, "status must not be null");
            reason = reason == null ? Optional.empty() : reason;
        }

        public EntryOutcome(
                AchEntryId entryId,
                String entryReference,
                AchEntryStatus status,
                PaymentReason reason) {
            this(entryId, entryReference, status, Optional.ofNullable(reason));
        }
    }
}
