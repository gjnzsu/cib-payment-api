package com.cib.payment.api.infrastructure.simulator;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.AchDirectCreditOutcome;
import com.cib.payment.api.application.port.AchDirectCreditSimulator;
import com.cib.payment.api.domain.model.AchBatchEntry;
import com.cib.payment.api.domain.model.AchBatchRecord;
import com.cib.payment.api.domain.model.AchBatchStatus;
import com.cib.payment.api.domain.model.AchEntryStatus;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.PaymentReason;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DeterministicAchDirectCreditSimulator implements AchDirectCreditSimulator {
    @Override
    public AchDirectCreditOutcome process(
            AchBatchRecord acceptedRecord,
            AuthorizationContext authorizationContext,
            String scenario) {
        Objects.requireNonNull(acceptedRecord, "acceptedRecord must not be null");
        Objects.requireNonNull(authorizationContext, "authorizationContext must not be null");

        if (scenario == null || scenario.isBlank()) {
            throw new ValidationFailureException("ACH direct credit simulator scenario is required");
        }

        return switch (scenario) {
            case "ach_direct_credit_accepted" -> outcome(
                    acceptedRecord,
                    AchBatchStatus.ACCEPTED_FOR_CLEARING,
                    AchEntryStatus.ACCEPTED,
                    null);
            case "ach_direct_credit_settled" -> outcome(
                    acceptedRecord,
                    AchBatchStatus.SETTLED,
                    AchEntryStatus.SETTLED,
                    null);
            case "ach_direct_credit_settlement_pending" -> outcome(
                    acceptedRecord,
                    AchBatchStatus.SETTLEMENT_PENDING,
                    AchEntryStatus.ACCEPTED,
                    new PaymentReason(
                            "ACH_SETTLEMENT_PENDING",
                            "ACH direct credit settlement is pending"));
            case "ach_direct_credit_partially_returned" -> partiallyReturnedOutcome(acceptedRecord);
            case "ach_direct_credit_rejected" -> outcome(
                    acceptedRecord,
                    AchBatchStatus.REJECTED,
                    AchEntryStatus.REJECTED,
                    new PaymentReason(
                            "ACH_BATCH_REJECTED",
                            "ACH direct credit batch was rejected"));
            default -> throw new ValidationFailureException(
                    "Unsupported ACH direct credit simulator scenario: " + scenario);
        };
    }

    private AchDirectCreditOutcome outcome(
            AchBatchRecord record,
            AchBatchStatus batchStatus,
            AchEntryStatus entryStatus,
            PaymentReason reason) {
        return new AchDirectCreditOutcome(
                batchStatus,
                record.entries().stream()
                        .map(entry -> entryOutcome(entry, entryStatus, null))
                        .toList(),
                Optional.ofNullable(reason));
    }

    private AchDirectCreditOutcome partiallyReturnedOutcome(AchBatchRecord record) {
        var returnReason = new PaymentReason(
                "ACH_ENTRY_RETURNED",
                "ACH direct credit entry was returned");
        var entryOutcomes = mapPartiallyReturnedEntries(record.entries(), returnReason);
        return new AchDirectCreditOutcome(
                AchBatchStatus.PARTIALLY_RETURNED,
                entryOutcomes,
                new PaymentReason(
                        "ACH_PARTIAL_RETURN",
                        "One or more ACH direct credit entries were returned"));
    }

    private List<AchDirectCreditOutcome.EntryOutcome> mapPartiallyReturnedEntries(
            List<AchBatchEntry> entries,
            PaymentReason returnReason) {
        if (entries.isEmpty()) {
            return List.of();
        }

        return java.util.stream.IntStream.range(0, entries.size())
                .mapToObj(index -> {
                    var entry = entries.get(index);
                    if (index == 0) {
                        return entryOutcome(entry, AchEntryStatus.RETURNED, returnReason);
                    }
                    return entryOutcome(entry, AchEntryStatus.SETTLED, null);
                })
                .toList();
    }

    private AchDirectCreditOutcome.EntryOutcome entryOutcome(
            AchBatchEntry entry,
            AchEntryStatus status,
            PaymentReason reason) {
        return new AchDirectCreditOutcome.EntryOutcome(
                entry.entryId(),
                entry.entryReference(),
                status,
                Optional.ofNullable(reason));
    }
}
