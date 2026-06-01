package com.cib.payment.api.infrastructure.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.AchBatchEntry;
import com.cib.payment.api.domain.model.AchBatchId;
import com.cib.payment.api.domain.model.AchBatchRecord;
import com.cib.payment.api.domain.model.AchBatchStatus;
import com.cib.payment.api.domain.model.AchEntryId;
import com.cib.payment.api.domain.model.AchEntryStatus;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentReason;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicAchDirectCreditSimulatorTest {
    private final DeterministicAchDirectCreditSimulator simulator =
            new DeterministicAchDirectCreditSimulator();

    @Test
    void acceptedScenarioAcceptsBatchAndEntriesForClearing() {
        var outcome = simulator.process(batchRecord(), authorizationContext(), "ach_direct_credit_accepted");

        assertThat(outcome.batchStatus()).isEqualTo(AchBatchStatus.ACCEPTED_FOR_CLEARING);
        assertThat(outcome.entryOutcomes())
                .extracting(entry -> entry.status())
                .containsExactly(AchEntryStatus.ACCEPTED, AchEntryStatus.ACCEPTED);
        assertThat(outcome.reason()).isEmpty();
    }

    @Test
    void settledScenarioSettlesBatchAndEntries() {
        var outcome = simulator.process(batchRecord(), authorizationContext(), "ach_direct_credit_settled");

        assertThat(outcome.batchStatus()).isEqualTo(AchBatchStatus.SETTLED);
        assertThat(outcome.entryOutcomes())
                .extracting(entry -> entry.status())
                .containsExactly(AchEntryStatus.SETTLED, AchEntryStatus.SETTLED);
        assertThat(outcome.reason()).isEmpty();
    }

    @Test
    void settlementPendingScenarioKeepsEntrySummariesAccepted() {
        var outcome = simulator.process(batchRecord(), authorizationContext(), "ach_direct_credit_settlement_pending");

        assertThat(outcome.batchStatus()).isEqualTo(AchBatchStatus.SETTLEMENT_PENDING);
        assertThat(outcome.entryOutcomes())
                .extracting(entry -> entry.status())
                .containsExactly(AchEntryStatus.ACCEPTED, AchEntryStatus.ACCEPTED);
        assertThat(outcome.reason()).hasValueSatisfying(reason -> {
            assertThat(reason.code()).isEqualTo("ACH_SETTLEMENT_PENDING");
            assertThat(reason.message()).contains("pending");
        });
    }

    @Test
    void partiallyReturnedScenarioReturnsFirstEntryAndLeavesOthersSettled() {
        var outcome = simulator.process(batchRecord(), authorizationContext(), "ach_direct_credit_partially_returned");

        assertThat(outcome.batchStatus()).isEqualTo(AchBatchStatus.PARTIALLY_RETURNED);
        assertThat(outcome.entryOutcomes())
                .extracting(entry -> entry.status())
                .containsExactly(AchEntryStatus.RETURNED, AchEntryStatus.SETTLED);
        assertThat(outcome.entryOutcomes().get(0).reason()).hasValueSatisfying(reason -> {
            assertThat(reason.code()).isEqualTo("ACH_ENTRY_RETURNED");
            assertThat(reason.message()).contains("returned");
        });
    }

    @Test
    void partiallyReturnedScenarioReturnsOnlyFirstPositionWhenEntriesAreValueEqual() {
        var outcome = simulator.process(
                batchRecordWithDuplicateEntries(),
                authorizationContext(),
                "ach_direct_credit_partially_returned");

        assertThat(outcome.batchStatus()).isEqualTo(AchBatchStatus.PARTIALLY_RETURNED);
        assertThat(outcome.entryOutcomes())
                .extracting(entry -> entry.status())
                .containsExactly(AchEntryStatus.RETURNED, AchEntryStatus.SETTLED);
        assertThat(outcome.entryOutcomes().get(0).reason()).isPresent();
        assertThat(outcome.entryOutcomes().get(1).reason()).isEmpty();
    }

    @Test
    void rejectedScenarioRejectsBatchAndEntries() {
        var outcome = simulator.process(batchRecord(), authorizationContext(), "ach_direct_credit_rejected");

        assertThat(outcome.batchStatus()).isEqualTo(AchBatchStatus.REJECTED);
        assertThat(outcome.entryOutcomes())
                .extracting(entry -> entry.status())
                .containsExactly(AchEntryStatus.REJECTED, AchEntryStatus.REJECTED);
        assertThat(outcome.reason()).hasValueSatisfying(reason -> {
            assertThat(reason.code()).isEqualTo("ACH_BATCH_REJECTED");
            assertThat(reason.message()).contains("rejected");
        });
    }

    @Test
    void rejectsUnsupportedScenario() {
        assertThatThrownBy(() -> simulator.process(batchRecord(), authorizationContext(), "unsupported"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsupported ACH direct credit simulator scenario");
    }

    @Test
    void rejectsBlankScenario() {
        assertThatThrownBy(() -> simulator.process(batchRecord(), authorizationContext(), "  "))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("ACH direct credit simulator scenario is required");
    }

    private AchBatchRecord batchRecord() {
        var now = Instant.parse("2026-06-01T00:00:00Z");
        return new AchBatchRecord(
                new AchBatchId(UUID.fromString("550e8400-e29b-41d4-a716-446655440200")),
                "client-ach-a",
                "BATCH-20260601-001",
                "Example Payroll LLC",
                LocalDate.parse("2026-06-02"),
                new AccountReference("021000021", "999900001111", "Example Payroll LLC"),
                List.of(
                        entry("550e8400-e29b-41d4-a716-446655440201", "ENTRY-001"),
                        entry("550e8400-e29b-41d4-a716-446655440202", "ENTRY-002")),
                AchBatchStatus.ACCEPTED_FOR_CLEARING,
                new CorrelationId("corr-ach-simulator"),
                now,
                now,
                Optional.empty());
    }

    private AchBatchRecord batchRecordWithDuplicateEntries() {
        var now = Instant.parse("2026-06-01T00:00:00Z");
        var duplicateEntry = entry("550e8400-e29b-41d4-a716-446655440201", "ENTRY-001");
        return new AchBatchRecord(
                new AchBatchId(UUID.fromString("550e8400-e29b-41d4-a716-446655440200")),
                "client-ach-a",
                "BATCH-20260601-001",
                "Example Payroll LLC",
                LocalDate.parse("2026-06-02"),
                new AccountReference("021000021", "999900001111", "Example Payroll LLC"),
                List.of(duplicateEntry, duplicateEntry),
                AchBatchStatus.ACCEPTED_FOR_CLEARING,
                new CorrelationId("corr-ach-simulator"),
                now,
                now,
                Optional.empty());
    }

    private AchBatchEntry entry(String entryId, String entryReference) {
        return new AchBatchEntry(
                new AchEntryId(UUID.fromString(entryId)),
                entryReference,
                "Receiver " + entryReference,
                new AccountReference("021000021", "111122223333", "Receiver " + entryReference),
                new Money("USD", "10.25"),
                new PaymentReason("PAYROLL", "Payroll credit"),
                AchEntryStatus.ACCEPTED,
                Optional.empty());
    }

    private AuthorizationContext authorizationContext() {
        return new AuthorizationContext(
                "ach-client-a",
                "ach-client-a",
                Set.of("ach-payments:create"),
                "tenant-a",
                Map.of(),
                Instant.parse("2026-06-01T00:00:00Z"),
                "jwt-id",
                new CorrelationId("corr-ach-simulator"));
    }
}
