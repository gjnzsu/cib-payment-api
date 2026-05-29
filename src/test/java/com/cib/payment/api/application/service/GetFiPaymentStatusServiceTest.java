package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.domain.model.AccountRelationshipRole;
import com.cib.payment.api.domain.model.AuthorizationContext;
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
import com.cib.payment.api.infrastructure.persistence.InMemoryFiPaymentRepository;
import com.cib.payment.api.infrastructure.persistence.InMemoryRecallInvestigationRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GetFiPaymentStatusServiceTest {
    private final InMemoryFiPaymentRepository fiPaymentRepository = new InMemoryFiPaymentRepository();
    private final InMemoryRecallInvestigationRepository recallRepository = new InMemoryRecallInvestigationRepository();
    private final GetFiPaymentStatusService service = new GetFiPaymentStatusService(fiPaymentRepository, recallRepository);

    @Test
    void ownerCanQueryOwnFiPayment() {
        var record = fiPaymentRecord("fi-client-a", FiPaymentStatus.PROCESSING, Optional.of(reason()));
        fiPaymentRepository.save(record);

        var response = service.getStatus(record.paymentId().value().toString(), authorizationContext("fi-client-a"));

        assertThat(response.paymentId()).isEqualTo(record.paymentId().value().toString());
        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.messageId()).isEqualTo("MSG-2026-0001");
        assertThat(response.instructionId()).isEqualTo("INSTR-0001");
        assertThat(response.originalPaymentReference()).isEqualTo("E2E-0001");
        assertThat(response.instructingAgentBic()).isEqualTo("CIBBHKHH");
        assertThat(response.instructedAgentBic()).isEqualTo("CORRUS33");
        assertThat(response.settlementCurrency()).isEqualTo("USD");
        assertThat(response.reason().code()).isEqualTo("FI_REASON");
        assertThat(response.correlationId()).isEqualTo("corr-fi-status");
        assertThat(response.links().self()).isEqualTo("/v1/fi-payments/" + record.paymentId().value());
        assertThat(response.recallInvestigation()).isNull();
    }

    @Test
    void unrelatedClientCannotQuery() {
        var record = fiPaymentRecord("fi-client-a", FiPaymentStatus.SETTLED, Optional.empty());
        fiPaymentRepository.save(record);

        assertThatThrownBy(() -> service.getStatus(
                        record.paymentId().value().toString(),
                        authorizationContext("fi-client-b")))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void unknownPaymentReturnsNotFound() {
        assertThatThrownBy(() -> service.getStatus(
                        UUID.randomUUID().toString(),
                        authorizationContext("fi-client-a")))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void invalidUuidFailsValidation() {
        assertThatThrownBy(() -> service.getStatus("not-a-uuid", authorizationContext("fi-client-a")))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("paymentId must be a UUID string");
    }

    @Test
    void latestRecallSummaryAppearsWhenRepositoryContainsRecallRecord() {
        var record = fiPaymentRecord("fi-client-a", FiPaymentStatus.PROCESSING, Optional.empty());
        fiPaymentRepository.save(record);
        recallRepository.saveIfAbsent(recallRecord(record.paymentId(), "fi-client-a"));

        var response = service.getStatus(record.paymentId().value().toString(), authorizationContext("fi-client-a"));

        assertThat(response.recallInvestigation()).isNotNull();
        assertThat(response.recallInvestigation().investigationId()).isNotBlank();
        assertThat(response.recallInvestigation().recallMessageId()).isEqualTo("RCL-MSG-001");
        assertThat(response.recallInvestigation().caseId()).isEqualTo("CASE-001");
        assertThat(response.recallInvestigation().originalPaymentReference()).isEqualTo("E2E-0001");
        assertThat(response.recallInvestigation().status()).isEqualTo("PENDING");
        assertThat(response.recallInvestigation().reasonCode()).isEqualTo("PENDING_REVIEW");
        assertThat(response.recallInvestigation().reasonMessage()).isEqualTo("Investigation pending correspondent response");
    }

    private FiPaymentRecord fiPaymentRecord(String ownerClientId, FiPaymentStatus status, Optional<PaymentReason> reason) {
        var now = Instant.parse("2026-05-28T00:00:00Z");
        return new FiPaymentRecord(
                new FiPaymentId(UUID.randomUUID()),
                ownerClientId,
                new FiPaymentIdentifiers("MSG-2026-0001", "INSTR-0001", "E2E-0001"),
                new FiParty("CIBBHKHH"),
                new FiParty("CORRUS33"),
                new Money("USD", "100000.00"),
                "USD",
                status,
                settlementContext(),
                new CorrelationId("corr-fi-status"),
                now,
                now,
                reason);
    }

    private RecallInvestigationRecord recallRecord(FiPaymentId paymentId, String ownerClientId) {
        var now = Instant.parse("2026-05-28T01:00:00Z");
        return new RecallInvestigationRecord(
                new RecallInvestigationId(UUID.randomUUID()),
                paymentId,
                ownerClientId,
                "RCL-MSG-001",
                "CASE-001",
                "E2E-0001",
                RecallInvestigationStatus.PENDING,
                Optional.of("PENDING_REVIEW"),
                Optional.of("Investigation pending correspondent response"),
                settlementContext(),
                new CorrelationId("corr-fi-recall"),
                now,
                now);
    }

    private CorrespondentSettlementContext settlementContext() {
        return new CorrespondentSettlementContext(
                new FiParty("CIBBHKHH"),
                new FiParty("CORRUS33"),
                Optional.of(new FiParty("CORRUS33")),
                "USD",
                AccountRelationshipRole.NOSTRO,
                "nostro-usd-corrus33-****1234");
    }

    private PaymentReason reason() {
        return new PaymentReason("FI_REASON", "FI payment reason");
    }

    private AuthorizationContext authorizationContext(String clientId) {
        return new AuthorizationContext(
                clientId,
                clientId,
                Set.of("payments:read"),
                "tenant-a",
                Map.of(),
                Instant.parse("2026-05-28T00:00:00Z"),
                "jwt-id",
                new CorrelationId("corr-fi-request"));
    }
}
