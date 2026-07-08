package com.cib.payment.api.infrastructure.simulator;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.CollectionSimulator;
import com.cib.payment.api.application.port.CollectionSimulatorOutcome;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CollectionEntry;
import com.cib.payment.api.domain.model.CollectionEntryStatus;
import com.cib.payment.api.domain.model.CollectionProfile;
import com.cib.payment.api.domain.model.CollectionRecord;
import com.cib.payment.api.domain.model.CollectionStatus;
import com.cib.payment.api.domain.model.PaymentReason;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class DeterministicCollectionSimulator implements CollectionSimulator {
    @Override
    public CollectionSimulatorOutcome process(
            CollectionRecord acceptedRecord,
            AuthorizationContext authorizationContext,
            String scenario) {
        Objects.requireNonNull(acceptedRecord, "acceptedRecord must not be null");
        Objects.requireNonNull(authorizationContext, "authorizationContext must not be null");
        if (scenario == null || scenario.isBlank()) {
            throw new ValidationFailureException("Collection simulator scenario is required");
        }

        return switch (scenario) {
            case "us_ach_debit_collected" -> requireProfile(
                    acceptedRecord,
                    CollectionProfile.US_ACH_DIRECT_DEBIT_BATCH,
                    entryOutcome(acceptedRecord, CollectionStatus.COLLECTED, CollectionEntryStatus.COLLECTED, null));
            case "us_ach_debit_settlement_pending" -> requireProfile(
                    acceptedRecord,
                    CollectionProfile.US_ACH_DIRECT_DEBIT_BATCH,
                    entryOutcome(
                            acceptedRecord,
                            CollectionStatus.SETTLEMENT_PENDING,
                            CollectionEntryStatus.PENDING_SETTLEMENT,
                            new PaymentReason("ACH_DEBIT_SETTLEMENT_PENDING", "ACH debit settlement is pending")));
            case "us_ach_debit_partially_returned" -> requireProfile(
                    acceptedRecord,
                    CollectionProfile.US_ACH_DIRECT_DEBIT_BATCH,
                    partiallyReturnedOutcome(acceptedRecord));
            case "us_ach_debit_rejected_authorization" -> rejected(
                    acceptedRecord,
                    "ACH_DEBIT_AUTHORIZATION_REJECTED",
                    "ACH debit authorization was rejected");
            case "us_ach_debit_rejected_insufficient_funds",
                    "hk_fps_collection_rejected_insufficient_funds" -> rejected(
                    acceptedRecord,
                    "COLLECTION_INSUFFICIENT_FUNDS",
                    "Collection was rejected for insufficient funds");
            case "hk_fps_collection_completed" -> requireProfile(
                    acceptedRecord,
                    CollectionProfile.HK_FPS_DIRECT_DEBIT,
                    new CollectionSimulatorOutcome(CollectionStatus.COMPLETED, List.of(), Optional.empty()));
            case "hk_fps_collection_pending_authorization" -> requireProfile(
                    acceptedRecord,
                    CollectionProfile.HK_FPS_DIRECT_DEBIT,
                    new CollectionSimulatorOutcome(
                            CollectionStatus.PENDING_AUTHORIZATION,
                            List.of(),
                            new PaymentReason(
                                    "HK_FPS_AUTHORIZATION_PENDING",
                                    "HK FPS direct debit authorization processing is pending")));
            case "hk_fps_collection_rejected_invalid_authorization" -> rejected(
                    acceptedRecord,
                    "HK_FPS_INVALID_AUTHORIZATION",
                    "HK FPS direct debit authorization is invalid");
            case "collection_timeout" -> entryOutcome(
                    acceptedRecord,
                    CollectionStatus.TIMEOUT,
                    CollectionEntryStatus.TIMEOUT,
                    new PaymentReason("COLLECTION_TIMEOUT", "Collection simulator timed out"));
            case "collection_internal_failure" -> entryOutcome(
                    acceptedRecord,
                    CollectionStatus.FAILED,
                    CollectionEntryStatus.FAILED,
                    new PaymentReason("COLLECTION_INTERNAL_FAILURE", "Collection simulator failed internally"));
            default -> throw new ValidationFailureException("Unsupported collection simulator scenario: " + scenario);
        };
    }

    private CollectionSimulatorOutcome requireProfile(
            CollectionRecord record,
            CollectionProfile profile,
            CollectionSimulatorOutcome outcome) {
        if (record.collectionProfile() != profile) {
            throw new ValidationFailureException("Collection simulator scenario does not match collection profile");
        }
        return outcome;
    }

    private CollectionSimulatorOutcome rejected(
            CollectionRecord record,
            String code,
            String message) {
        return entryOutcome(
                record,
                CollectionStatus.REJECTED,
                CollectionEntryStatus.REJECTED,
                new PaymentReason(code, message));
    }

    private CollectionSimulatorOutcome entryOutcome(
            CollectionRecord record,
            CollectionStatus status,
            CollectionEntryStatus entryStatus,
            PaymentReason reason) {
        return new CollectionSimulatorOutcome(
                status,
                record.entries().stream()
                        .map(entry -> entryOutcome(entry, entryStatus, reason))
                        .toList(),
                Optional.ofNullable(reason));
    }

    private CollectionSimulatorOutcome partiallyReturnedOutcome(CollectionRecord record) {
        var returnReason = new PaymentReason("ACH_DEBIT_ENTRY_RETURNED", "ACH debit entry was returned");
        var entryOutcomes = java.util.stream.IntStream.range(0, record.entries().size())
                .mapToObj(index -> {
                    var entry = record.entries().get(index);
                    if (index == 0) {
                        return entryOutcome(entry, CollectionEntryStatus.RETURNED, returnReason);
                    }
                    return entryOutcome(entry, CollectionEntryStatus.COLLECTED, null);
                })
                .toList();
        return new CollectionSimulatorOutcome(
                CollectionStatus.PARTIALLY_RETURNED,
                entryOutcomes,
                new PaymentReason("ACH_DEBIT_PARTIAL_RETURN", "One or more ACH debit entries were returned"));
    }

    private CollectionSimulatorOutcome.EntryOutcome entryOutcome(
            CollectionEntry entry,
            CollectionEntryStatus status,
            PaymentReason reason) {
        return new CollectionSimulatorOutcome.EntryOutcome(
                entry.entryId(),
                entry.entryReference(),
                status,
                Optional.ofNullable(reason));
    }
}
