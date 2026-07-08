package com.cib.payment.api.application.port;

import com.cib.payment.api.domain.model.CollectionEntryId;
import com.cib.payment.api.domain.model.CollectionEntryStatus;
import com.cib.payment.api.domain.model.CollectionStatus;
import com.cib.payment.api.domain.model.PaymentReason;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record CollectionSimulatorOutcome(
        CollectionStatus status,
        List<EntryOutcome> entryOutcomes,
        Optional<PaymentReason> reason) {
    public CollectionSimulatorOutcome {
        Objects.requireNonNull(status, "status must not be null");
        entryOutcomes = List.copyOf(Objects.requireNonNull(entryOutcomes, "entryOutcomes must not be null"));
        reason = reason == null ? Optional.empty() : reason;
    }

    public CollectionSimulatorOutcome(
            CollectionStatus status,
            List<EntryOutcome> entryOutcomes,
            PaymentReason reason) {
        this(status, entryOutcomes, Optional.ofNullable(reason));
    }

    public record EntryOutcome(
            CollectionEntryId entryId,
            String entryReference,
            CollectionEntryStatus status,
            Optional<PaymentReason> reason) {
        public EntryOutcome {
            Objects.requireNonNull(entryId, "entryId must not be null");
            Objects.requireNonNull(entryReference, "entryReference must not be null");
            Objects.requireNonNull(status, "status must not be null");
            reason = reason == null ? Optional.empty() : reason;
        }

        public EntryOutcome(
                CollectionEntryId entryId,
                String entryReference,
                CollectionEntryStatus status,
                PaymentReason reason) {
            this(entryId, entryReference, status, Optional.ofNullable(reason));
        }
    }
}
