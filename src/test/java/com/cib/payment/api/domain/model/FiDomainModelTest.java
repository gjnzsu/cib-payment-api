package com.cib.payment.api.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FiDomainModelTest {
    @Test
    void fiPaymentRecordKeepsOwnerIdentifiersStatusCorrelationAndCorrespondentContext() {
        var now = Instant.parse("2026-05-28T00:00:00Z");
        var identifiers = new FiPaymentIdentifiers("MSG-2026-0001", "INSTR-0001", "E2E-0001");
        var instructingParty = new FiParty("CIBBMYKLXXX");
        var instructedParty = new FiParty("IRVTUS3NXXX");
        var correspondent = new FiParty("CHASUS33XXX");
        var context = new CorrespondentSettlementContext(
                instructingParty,
                instructedParty,
                Optional.of(correspondent),
                "USD",
                AccountRelationshipRole.NOSTRO,
                "SIM-USD-****-0421");

        var record = new FiPaymentRecord(
                new FiPaymentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440001")),
                "fi-client-a",
                identifiers,
                instructingParty,
                instructedParty,
                new Money("USD", "100000.00"),
                "USD",
                FiPaymentStatus.PROCESSING,
                context,
                new CorrelationId("corr-fi-123"),
                now,
                now,
                Optional.of(new PaymentReason("CORRESPONDENT_REVIEW", "Pending correspondent review")));

        assertThat(record.ownerClientId()).isEqualTo("fi-client-a");
        assertThat(record.identifiers()).isEqualTo(identifiers);
        assertThat(record.status()).isEqualTo(FiPaymentStatus.PROCESSING);
        assertThat(record.correlationId()).isEqualTo(new CorrelationId("corr-fi-123"));
        assertThat(record.correspondentSettlementContext()).isEqualTo(context);
        assertThat(record.correspondentSettlementContext().accountRelationshipRole())
                .isEqualTo(AccountRelationshipRole.NOSTRO);
        assertThat(record.correspondentSettlementContext().maskedSimulatedAccountReference())
                .isEqualTo("SIM-USD-****-0421");
    }

    @Test
    void fiPaymentCandidateKeepsSupportedBusinessFields() {
        var identifiers = new FiPaymentIdentifiers("MSG-2026-0002", "INSTR-0002", "E2E-0002");
        var candidate = new FiPaymentCandidate(
                identifiers,
                new FiParty("CIBBMYKLXXX"),
                new FiParty("IRVTUS3NXXX"),
                Optional.of("CHASUS33XXX"),
                new Money("USD", "2500.00"),
                "USD",
                LocalDate.parse("2026-05-28"),
                "pacs.009.001.08");

        assertThat(candidate.identifiers()).isEqualTo(identifiers);
        assertThat(candidate.intermediaryBic()).contains("CHASUS33XXX");
        assertThat(candidate.settlementCurrency()).isEqualTo("USD");
        assertThat(candidate.sourceMessageType()).isEqualTo("pacs.009.001.08");
    }
}
