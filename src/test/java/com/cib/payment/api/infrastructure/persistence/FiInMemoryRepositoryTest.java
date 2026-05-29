package com.cib.payment.api.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.domain.model.AccountRelationshipRole;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.CorrespondentSettlementContext;
import com.cib.payment.api.domain.model.FiParty;
import com.cib.payment.api.domain.model.FiPaymentId;
import com.cib.payment.api.domain.model.FiPaymentIdentifiers;
import com.cib.payment.api.domain.model.FiPaymentRecord;
import com.cib.payment.api.domain.model.FiPaymentStatus;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.RecallInvestigationId;
import com.cib.payment.api.domain.model.RecallInvestigationRecord;
import com.cib.payment.api.domain.model.RecallInvestigationStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FiInMemoryRepositoryTest {
    @Test
    void fiPaymentRepositorySavesAndFindsByIdAndOwner() {
        var repository = new InMemoryFiPaymentRepository();
        var record = fiPaymentRecord("fi-client-a");

        repository.save(record);

        assertThat(repository.findById(record.paymentId())).contains(record);
        assertThat(repository.findByIdAndOwnerClientId(record.paymentId(), "fi-client-a")).contains(record);
        assertThat(repository.findByIdAndOwnerClientId(record.paymentId(), "fi-client-b")).isEmpty();
    }

    @Test
    void recallSaveIfAbsentKeepsFirstRecordForPayment() {
        var repository = new InMemoryRecallInvestigationRepository();
        var paymentId = new FiPaymentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440011"));
        var first = recallRecord(paymentId, "fi-client-a", "CASE-001");
        var duplicate = recallRecord(paymentId, "fi-client-a", "CASE-002");

        assertThat(repository.saveIfAbsent(first)).isEqualTo(first);
        assertThat(repository.saveIfAbsent(duplicate)).isEqualTo(first);
        assertThat(repository.findByPaymentId(paymentId)).contains(first);
    }

    @Test
    void recallRepositoryFindsByPaymentIdAndOwnerOnly() {
        var repository = new InMemoryRecallInvestigationRepository();
        var record = recallRecord(
                new FiPaymentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440012")),
                "fi-client-a",
                "CASE-003");

        repository.saveIfAbsent(record);

        assertThat(repository.findByPaymentIdAndOwnerClientId(record.fiPaymentId(), "fi-client-a")).contains(record);
        assertThat(repository.findByPaymentIdAndOwnerClientId(record.fiPaymentId(), "fi-client-b")).isEmpty();
    }

    private FiPaymentRecord fiPaymentRecord(String ownerClientId) {
        var now = Instant.parse("2026-05-28T00:00:00Z");
        return new FiPaymentRecord(
                new FiPaymentId(UUID.fromString("550e8400-e29b-41d4-a716-446655440010")),
                ownerClientId,
                identifiers(),
                instructingParty(),
                instructedParty(),
                new Money("USD", "100000.00"),
                "USD",
                FiPaymentStatus.SETTLED,
                settlementContext(),
                new CorrelationId("corr-fi-123"),
                now,
                now,
                Optional.empty());
    }

    private RecallInvestigationRecord recallRecord(FiPaymentId paymentId, String ownerClientId, String caseId) {
        var now = Instant.parse("2026-05-28T00:00:00Z");
        return new RecallInvestigationRecord(
                new RecallInvestigationId(UUID.randomUUID()),
                paymentId,
                ownerClientId,
                "RCL-MSG-001",
                caseId,
                "E2E-0001",
                RecallInvestigationStatus.PENDING,
                Optional.of("PENDING_REVIEW"),
                Optional.of("Investigation pending correspondent response"),
                settlementContext(),
                new CorrelationId("corr-fi-456"),
                now,
                now);
    }

    private FiPaymentIdentifiers identifiers() {
        return new FiPaymentIdentifiers("MSG-2026-0001", "INSTR-0001", "E2E-0001");
    }

    private FiParty instructingParty() {
        return new FiParty("CIBBMYKLXXX");
    }

    private FiParty instructedParty() {
        return new FiParty("IRVTUS3NXXX");
    }

    private CorrespondentSettlementContext settlementContext() {
        return new CorrespondentSettlementContext(
                instructingParty(),
                instructedParty(),
                Optional.of(new FiParty("CHASUS33XXX")),
                "USD",
                AccountRelationshipRole.NOSTRO,
                "SIM-USD-****-0421");
    }
}
