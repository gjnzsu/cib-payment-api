package com.cib.payment.api.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cib.payment.api.application.port.RtgsPaymentOutcome;
import com.cib.payment.api.application.port.RtgsPaymentRepository;
import com.cib.payment.api.application.port.RtgsPaymentSimulator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RtgsDomainModelTest {
    @Test
    void rtgsPaymentStatusContainsSettlementLifecycleStates() {
        assertThat(Arrays.stream(RtgsPaymentStatus.values()).map(Enum::name))
                .containsExactly(
                        "ACCEPTED_FOR_SETTLEMENT",
                        "QUEUED_FOR_LIQUIDITY",
                        "SETTLED",
                        "REJECTED");
    }

    @Test
    void rtgsClientSegmentContainsSupportedSegments() {
        assertThat(Arrays.stream(RtgsClientSegment.values()).map(Enum::name))
                .containsExactly("CORPORATE", "FI");
    }

    @Test
    void corporateRtgsPaymentRecordKeepsAccountPartiesSettlementMetadataStatusFinalityAndCorrelation() {
        var now = Instant.parse("2026-06-01T00:00:00Z");
        var reason = new PaymentReason("LIQUIDITY_QUEUE", "Queued pending liquidity");
        var debtorAccount = new AccountReference("021000021", "111122223333", "Sender Treasury");
        var creditorAccount = new AccountReference("021000021", "444455556666", "Receiver Treasury");

        var record = new RtgsPaymentRecord(
                new RtgsPaymentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440501")),
                "client-rtgs-a",
                RtgsClientSegment.CORPORATE,
                "RTGS-20260601-001",
                new RtgsPaymentRecord.CorporateParties(debtorAccount, creditorAccount),
                new Money("USD", "2500000.00"),
                LocalDate.parse("2026-06-01"),
                "URGENT",
                "Treasury settlement",
                RtgsPaymentStatus.QUEUED_FOR_LIQUIDITY,
                false,
                new CorrelationId("corr-rtgs-123"),
                now,
                now,
                Optional.of(reason));

        assertThat(record.ownerClientId()).isEqualTo("client-rtgs-a");
        assertThat(record.clientSegment()).isEqualTo(RtgsClientSegment.CORPORATE);
        assertThat(record.paymentReference()).isEqualTo("RTGS-20260601-001");
        assertThat(record.debtorAccount()).contains(debtorAccount);
        assertThat(record.creditorAccount()).contains(creditorAccount);
        assertThat(record.instructingAgentBic()).isEmpty();
        assertThat(record.instructedAgentBic()).isEmpty();
        assertThat(record.settlementAmount()).isEqualTo(new Money("USD", "2500000.00"));
        assertThat(record.requestedSettlementDate()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(record.settlementPriority()).isEqualTo("URGENT");
        assertThat(record.purpose()).isEqualTo("Treasury settlement");
        assertThat(record.status()).isEqualTo(RtgsPaymentStatus.QUEUED_FOR_LIQUIDITY);
        assertThat(record.settlementFinality()).isFalse();
        assertThat(record.correlationId()).isEqualTo(new CorrelationId("corr-rtgs-123"));
        assertThat(record.reason()).contains(reason);
    }

    @Test
    void fiRtgsPaymentRecordKeepsAgentPartiesSettlementMetadataStatusFinalityAndCorrelation() {
        var now = Instant.parse("2026-06-01T00:00:00Z");

        var record = new RtgsPaymentRecord(
                new RtgsPaymentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440503")),
                "client-rtgs-fi",
                RtgsClientSegment.FI,
                "RTGS-FI-20260601-001",
                new RtgsPaymentRecord.FiParties("CIBBMYKLXXX", "IRVTUS3NXXX"),
                new Money("USD", "2500000.00"),
                LocalDate.parse("2026-06-01"),
                "NORMAL",
                "FI liquidity transfer",
                RtgsPaymentStatus.ACCEPTED_FOR_SETTLEMENT,
                false,
                new CorrelationId("corr-rtgs-fi-123"),
                now,
                now,
                Optional.empty());

        assertThat(record.clientSegment()).isEqualTo(RtgsClientSegment.FI);
        assertThat(record.instructingAgentBic()).contains("CIBBMYKLXXX");
        assertThat(record.instructedAgentBic()).contains("IRVTUS3NXXX");
        assertThat(record.debtorAccount()).isEmpty();
        assertThat(record.creditorAccount()).isEmpty();
        assertThat(record.requestedSettlementDate()).isEqualTo(LocalDate.parse("2026-06-01"));
        assertThat(record.settlementPriority()).isEqualTo("NORMAL");
        assertThat(record.purpose()).isEqualTo("FI liquidity transfer");
    }

    @Test
    void rtgsPaymentRecordRejectsSegmentSpecificInvalidParties() {
        var now = Instant.parse("2026-06-01T00:00:00Z");
        var debtorAccount = new AccountReference("021000021", "111122223333", "Sender Treasury");
        var creditorAccount = new AccountReference("021000021", "444455556666", "Receiver Treasury");

        assertThrows(NullPointerException.class, () -> new RtgsPaymentRecord.CorporateParties(null, creditorAccount));
        assertThrows(NullPointerException.class, () -> new RtgsPaymentRecord.CorporateParties(debtorAccount, null));
        assertThrows(IllegalArgumentException.class, () -> new RtgsPaymentRecord.FiParties(" ", "IRVTUS3NXXX"));
        assertThrows(IllegalArgumentException.class, () -> new RtgsPaymentRecord.FiParties("CIBBMYKLXXX", ""));
        assertThrows(IllegalArgumentException.class, () -> new RtgsPaymentRecord(
                new RtgsPaymentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440504")),
                "client-rtgs-a",
                RtgsClientSegment.CORPORATE,
                "RTGS-20260601-003",
                new RtgsPaymentRecord.FiParties("CIBBMYKLXXX", "IRVTUS3NXXX"),
                new Money("USD", "100000.00"),
                LocalDate.parse("2026-06-01"),
                "URGENT",
                "Treasury settlement",
                RtgsPaymentStatus.ACCEPTED_FOR_SETTLEMENT,
                false,
                new CorrelationId("corr-rtgs-789"),
                now,
                now,
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new RtgsPaymentRecord(
                new RtgsPaymentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440505")),
                "client-rtgs-fi",
                RtgsClientSegment.FI,
                "RTGS-FI-20260601-002",
                new RtgsPaymentRecord.CorporateParties(debtorAccount, creditorAccount),
                new Money("USD", "100000.00"),
                LocalDate.parse("2026-06-01"),
                "NORMAL",
                "FI liquidity transfer",
                RtgsPaymentStatus.ACCEPTED_FOR_SETTLEMENT,
                false,
                new CorrelationId("corr-rtgs-790"),
                now,
                now,
                Optional.empty()));
    }

    @Test
    void rtgsPaymentOutcomeOnlyHasSettlementFinalityForSettledStatus() {
        var settled = new RtgsPaymentOutcome(
                RtgsPaymentStatus.SETTLED,
                true,
                Optional.of(new PaymentReason("SETTLED", "Final settlement completed")));
        var queued = new RtgsPaymentOutcome(
                RtgsPaymentStatus.QUEUED_FOR_LIQUIDITY,
                false,
                Optional.empty());
        var rejected = new RtgsPaymentOutcome(
                RtgsPaymentStatus.REJECTED,
                false,
                new PaymentReason("INSUFFICIENT_LIQUIDITY", "Liquidity unavailable"));

        assertThat(settled.settlementFinality()).isTrue();
        assertThat(queued.settlementFinality()).isFalse();
        assertThat(rejected.settlementFinality()).isFalse();
        assertThat(rejected.reason())
                .contains(new PaymentReason("INSUFFICIENT_LIQUIDITY", "Liquidity unavailable"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RtgsPaymentOutcome(RtgsPaymentStatus.REJECTED, true, Optional.empty()));
    }

    @Test
    void rtgsPortsExposeRepositoryAndSimulatorContracts() {
        var paymentId = new RtgsPaymentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440502"));
        var record = sampleRecord(paymentId, RtgsPaymentStatus.ACCEPTED_FOR_SETTLEMENT, false);
        RtgsPaymentRepository repository = new RtgsPaymentRepository() {
            private RtgsPaymentRecord saved;

            @Override
            public RtgsPaymentRecord save(RtgsPaymentRecord record) {
                saved = record;
                return saved;
            }

            @Override
            public Optional<RtgsPaymentRecord> find(RtgsPaymentId paymentId) {
                return saved != null && saved.paymentId().equals(paymentId)
                        ? Optional.of(saved)
                        : Optional.empty();
            }
        };
        RtgsPaymentSimulator simulator = (acceptedRecord, authorizationContext, scenario) ->
                new RtgsPaymentOutcome(RtgsPaymentStatus.SETTLED, true, Optional.empty());

        var authorizationContext = new AuthorizationContext(
                "client-rtgs-a",
                "subject-rtgs-a",
                java.util.Set.of("payments:create"),
                null,
                java.util.Map.of(),
                Instant.parse("2026-06-01T00:00:00Z"),
                null,
                new CorrelationId("corr-rtgs-456"));

        assertThat(repository.save(record)).isEqualTo(record);
        assertThat(repository.find(paymentId)).contains(record);
        assertThat(simulator.process(record, authorizationContext, "success"))
                .isEqualTo(new RtgsPaymentOutcome(RtgsPaymentStatus.SETTLED, true, Optional.empty()));
    }

    private static RtgsPaymentRecord sampleRecord(
            RtgsPaymentId paymentId,
            RtgsPaymentStatus status,
            boolean settlementFinality) {
        var now = Instant.parse("2026-06-01T00:00:00Z");
        return new RtgsPaymentRecord(
                paymentId,
                "client-rtgs-a",
                RtgsClientSegment.CORPORATE,
                "RTGS-20260601-002",
                new RtgsPaymentRecord.CorporateParties(
                        new AccountReference("021000021", "111122223333", "Sender Treasury"),
                        new AccountReference("021000021", "444455556666", "Receiver Treasury")),
                new Money("USD", "100000.00"),
                LocalDate.parse("2026-06-01"),
                "NORMAL",
                "Treasury settlement",
                status,
                settlementFinality,
                new CorrelationId("corr-rtgs-456"),
                now,
                now,
                Optional.empty());
    }
}
