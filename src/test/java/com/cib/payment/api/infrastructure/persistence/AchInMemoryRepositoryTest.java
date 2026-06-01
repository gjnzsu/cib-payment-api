package com.cib.payment.api.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.AchBatchEntry;
import com.cib.payment.api.domain.model.AchBatchId;
import com.cib.payment.api.domain.model.AchBatchRecord;
import com.cib.payment.api.domain.model.AchBatchStatus;
import com.cib.payment.api.domain.model.AchEntryId;
import com.cib.payment.api.domain.model.AchEntryStatus;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentReason;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AchInMemoryRepositoryTest {
    @Test
    void savesAndFindsAchBatchRecordsByBatchId() {
        var repository = new InMemoryAchBatchRepository();
        var record = batchRecord(AchBatchStatus.ACCEPTED_FOR_CLEARING);

        var saved = repository.save(record);

        assertThat(saved).isEqualTo(record);
        assertThat(repository.find(record.batchId())).contains(record);
    }

    @Test
    void overwritesExistingAchBatchRecordForSameBatchId() {
        var repository = new InMemoryAchBatchRepository();
        var original = batchRecord(AchBatchStatus.ACCEPTED_FOR_CLEARING);
        var updated = batchRecord(AchBatchStatus.SETTLED);

        repository.save(original);
        repository.save(updated);

        assertThat(repository.find(original.batchId())).contains(updated);
    }

    @Test
    void returnsEmptyWhenAchBatchRecordIsUnknown() {
        var repository = new InMemoryAchBatchRepository();

        assertThat(repository.find(new AchBatchId(UUID.fromString("550e8400-e29b-41d4-a716-446655440299"))))
                .isEmpty();
    }

    private AchBatchRecord batchRecord(AchBatchStatus status) {
        var now = Instant.parse("2026-06-01T00:00:00Z");
        return new AchBatchRecord(
                new AchBatchId(UUID.fromString("550e8400-e29b-41d4-a716-446655440200")),
                "client-ach-a",
                "BATCH-20260601-001",
                "Example Payroll LLC",
                LocalDate.parse("2026-06-02"),
                new AccountReference("021000021", "999900001111", "Example Payroll LLC"),
                List.of(new AchBatchEntry(
                        new AchEntryId(UUID.fromString("550e8400-e29b-41d4-a716-446655440201")),
                        "ENTRY-001",
                        "Alice Receiver",
                        new AccountReference("021000021", "111122223333", "Alice Receiver"),
                        new Money("USD", "10.25"),
                        new PaymentReason("PAYROLL", "Payroll credit"),
                        AchEntryStatus.ACCEPTED,
                        Optional.empty())),
                status,
                new CorrelationId("corr-ach-repository"),
                now,
                now,
                Optional.empty());
    }
}
