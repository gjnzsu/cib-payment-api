package com.cib.payment.api.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AchDomainModelTest {
    @Test
    void achBatchStatusContainsDirectCreditLifecycleStates() {
        assertThat(Arrays.stream(AchBatchStatus.values()).map(Enum::name))
                .containsExactly(
                        "ACCEPTED_FOR_CLEARING",
                        "SETTLEMENT_PENDING",
                        "SETTLED",
                        "PARTIALLY_RETURNED",
                        "REJECTED");
    }

    @Test
    void achEntryStatusContainsSupportedEntryLifecycleStates() {
        assertThat(Arrays.stream(AchEntryStatus.values()).map(Enum::name))
                .containsExactly("ACCEPTED", "SETTLED", "RETURNED", "REJECTED");
    }

    @Test
    void achBatchRecordPreservesClientCorrelationEntriesAndDerivesTotals() {
        var now = Instant.parse("2026-06-01T00:00:00Z");
        var firstEntry = new AchBatchEntry(
                new AchEntryId(UUID.fromString("550e8400-e29b-41d4-a716-446655440101")),
                "ENTRY-001",
                "Alice Receiver",
                new AccountReference("021000021", "111122223333", "Alice Receiver"),
                new Money("USD", "10.25"),
                new PaymentReason("PAYROLL", "Payroll credit"),
                AchEntryStatus.ACCEPTED,
                Optional.empty());
        var secondEntry = new AchBatchEntry(
                new AchEntryId(UUID.fromString("550e8400-e29b-41d4-a716-446655440102")),
                "ENTRY-002",
                "Bob Receiver",
                new AccountReference("021000021", "444455556666", "Bob Receiver"),
                new Money("USD", "20.25"),
                new PaymentReason("PAYROLL", "Payroll credit"),
                AchEntryStatus.RETURNED,
                Optional.of(new PaymentReason("R03", "No account/unable to locate account")));

        var record = new AchBatchRecord(
                new AchBatchId(UUID.fromString("550e8400-e29b-41d4-a716-446655440100")),
                "client-ach-a",
                "BATCH-20260601-001",
                "Example Payroll LLC",
                LocalDate.parse("2026-06-02"),
                new AccountReference("021000021", "999900001111", "Example Payroll LLC"),
                List.of(firstEntry, secondEntry),
                AchBatchStatus.PARTIALLY_RETURNED,
                new CorrelationId("corr-ach-123"),
                now,
                now,
                Optional.of(new PaymentReason("ACH_PARTIAL_RETURN", "One entry was returned")));

        assertThat(record.clientId()).isEqualTo("client-ach-a");
        assertThat(record.correlationId()).isEqualTo(new CorrelationId("corr-ach-123"));
        assertThat(record.entries()).containsExactly(firstEntry, secondEntry);
        assertThat(record.entryCount()).isEqualTo(2);
        assertThat(record.totalAmount()).isEqualTo(new Money("USD", "30.50"));
    }
}
