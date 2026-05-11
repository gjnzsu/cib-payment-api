package com.cib.payment.api.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentInstruction;
import com.cib.payment.api.domain.model.PaymentRecord;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryRepositoryTest {
    @Test
    void idempotencySaveIfAbsentReturnsOriginalRecordForDuplicates() {
        var repository = new InMemoryIdempotencyRepository();
        var original = idempotencyRecord("fingerprint-a");
        var duplicate = idempotencyRecord("fingerprint-b");

        assertThat(repository.saveIfAbsent(original)).isEqualTo(original);
        assertThat(repository.saveIfAbsent(duplicate)).isEqualTo(original);
        assertThat(repository.find("client-a", "key-1")).contains(original);
    }

    @Test
    void paymentStatusRepositoryFindsByPaymentIdAndClientOnly() {
        var repository = new InMemoryPaymentStatusRepository();
        var record = paymentRecord(PaymentStatus.ACCEPTED);

        repository.save(record);

        assertThat(repository.findByPaymentIdAndClientId(record.paymentId(), "client-a")).contains(record);
        assertThat(repository.findByPaymentIdAndClientId(record.paymentId(), "client-b")).isEmpty();
    }

    @Test
    void paymentStatusRepositoryUpdatesStatusAndReason() {
        var repository = new InMemoryPaymentStatusRepository();
        var record = repository.save(paymentRecord(PaymentStatus.ACCEPTED));
        var reason = new PaymentReason("MOCK_REJECTION", "Payment rejected by downstream mock");

        var updated = repository.updateStatus(record.paymentId(), PaymentStatus.REJECTED, reason);

        assertThat(updated.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(updated.reason()).contains(reason);
        assertThat(updated.updatedAt()).isAfterOrEqualTo(record.updatedAt());
    }

    private IdempotencyRecord idempotencyRecord(String fingerprint) {
        var now = Instant.parse("2026-05-09T00:00:00Z");
        return new IdempotencyRecord(
                "client-a",
                "key-1",
                fingerprint,
                new PaymentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440000")),
                PaymentStatus.ACCEPTED,
                new CorrelationId("corr-123"),
                now,
                now);
    }

    private PaymentRecord paymentRecord(PaymentStatus status) {
        var now = Instant.parse("2026-05-09T00:00:00Z");
        return new PaymentRecord(
                new PaymentId(UUID.randomUUID()),
                "client-a",
                new PaymentInstruction(
                        new AccountReference("CIBBMYKL", "1234567890", "Acme Treasury"),
                        new AccountReference("PAYBMYKL", "9876543210", "Supplier Sdn Bhd"),
                        new Money("MYR", "1250.50"),
                        "INV-2026-0001",
                        "Invoice payment",
                        null),
                status,
                now,
                now,
                new CorrelationId("corr-123"),
                Optional.empty());
    }
}
