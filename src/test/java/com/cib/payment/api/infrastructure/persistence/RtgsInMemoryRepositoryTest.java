package com.cib.payment.api.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.RtgsClientSegment;
import com.cib.payment.api.domain.model.RtgsPaymentId;
import com.cib.payment.api.domain.model.RtgsPaymentRecord;
import com.cib.payment.api.domain.model.RtgsPaymentStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RtgsInMemoryRepositoryTest {
    @Test
    void savesAndFindsRtgsPaymentRecordByPaymentId() {
        var repository = new InMemoryRtgsPaymentRepository();
        var record = rtgsPaymentRecord(RtgsPaymentStatus.ACCEPTED_FOR_SETTLEMENT, false, Optional.empty());

        var saved = repository.save(record);

        assertThat(saved).isEqualTo(record);
        assertThat(repository.find(record.paymentId())).contains(record);
    }

    @Test
    void overwritesExistingRtgsPaymentRecordForSamePaymentId() {
        var repository = new InMemoryRtgsPaymentRepository();
        var original = rtgsPaymentRecord(RtgsPaymentStatus.ACCEPTED_FOR_SETTLEMENT, false, Optional.empty());
        var updated = rtgsPaymentRecord(
                RtgsPaymentStatus.SETTLED,
                true,
                Optional.empty());

        repository.save(original);
        repository.save(updated);

        assertThat(repository.find(original.paymentId())).contains(updated);
    }

    @Test
    void returnsEmptyWhenRtgsPaymentRecordIsUnknown() {
        var repository = new InMemoryRtgsPaymentRepository();

        assertThat(repository.find(new RtgsPaymentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440699"))))
                .isEmpty();
    }

    private RtgsPaymentRecord rtgsPaymentRecord(
            RtgsPaymentStatus status,
            boolean settlementFinality,
            Optional<PaymentReason> reason) {
        var now = Instant.parse("2026-06-01T00:00:00Z");
        return new RtgsPaymentRecord(
                new RtgsPaymentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440600")),
                "rtgs-client-a",
                RtgsClientSegment.CORPORATE,
                "RTGS-REF-001",
                new RtgsPaymentRecord.CorporateParties(
                        new AccountReference("021000021", "123456789012", "Example Debtor LLC"),
                        new AccountReference("021000021", "210987654321", "Example Creditor LLC")),
                new Money("USD", "250000.00"),
                LocalDate.parse("2026-06-01"),
                "HIGH",
                "Treasury payment",
                status,
                settlementFinality,
                new CorrelationId("corr-rtgs-repository"),
                now,
                now,
                reason);
    }
}
