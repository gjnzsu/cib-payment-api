package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.IdempotencyRepository;
import com.cib.payment.api.application.port.RecallInvestigationRepository;
import com.cib.payment.api.application.port.RecallInvestigationResponseRenderer;
import com.cib.payment.api.domain.model.IdempotencyRecord;
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
import com.cib.payment.api.infrastructure.iso.Camt056Parser;
import com.cib.payment.api.infrastructure.persistence.InMemoryFiPaymentRepository;
import com.cib.payment.api.infrastructure.persistence.InMemoryIdempotencyRepository;
import com.cib.payment.api.infrastructure.persistence.InMemoryRecallInvestigationRepository;
import com.cib.payment.api.infrastructure.simulator.DeterministicRecallInvestigationSimulator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateRecallInvestigationServiceTest {
    private final InMemoryFiPaymentRepository fiPaymentRepository = new InMemoryFiPaymentRepository();
    private final InMemoryRecallInvestigationRepository recallRepository = new InMemoryRecallInvestigationRepository();
    private final CreateRecallInvestigationService service = new CreateRecallInvestigationService(
            new Camt056Parser(),
            new DeterministicRecallInvestigationSimulator(),
            new TestRecallInvestigationResponseRenderer(),
            fiPaymentRepository,
            recallRepository,
            new InMemoryIdempotencyRepository(),
            new RequestFingerprintService());

    @Test
    void settledPaymentAllowsRecallAndReturnsCamt029() throws Exception {
        var payment = storePayment("fi-client-a", FiPaymentStatus.SETTLED);

        var responseXml = service.create(
                payment.paymentId().value().toString(),
                readFixture("camt056-recall-accepted.xml"),
                "application/camt.056+xml",
                authorizationContext("fi-client-a", "corr-fi-recall-settled"),
                "idem-recall-settled",
                "recall_accepted");

        var recall = recallRepository.findByPaymentId(payment.paymentId()).orElseThrow();
        assertThat(recall.fiPaymentId()).isEqualTo(payment.paymentId());
        assertThat(recall.ownerClientId()).isEqualTo("fi-client-a");
        assertThat(recall.recallMessageId()).isEqualTo("FICLIENT01-CAMT056-RECALL-ACCEPTED");
        assertThat(recall.originalPaymentReference()).isEqualTo("FI-E2E-20260528-0001");
        assertThat(recall.status().name()).isEqualTo("ACCEPTED");
        assertThat(recall.settlementContext()).isEqualTo(payment.correspondentSettlementContext());
        assertThat(recall.correlationId().value()).isEqualTo("corr-fi-recall-settled");
        assertThat(responseXml)
                .contains("camt029-" + recall.investigationId().value())
                .contains("CNCL")
                .contains("corr-fi-recall-settled");
    }

    @Test
    void processingPaymentAllowsRecall() throws Exception {
        var payment = storePayment("fi-client-a", FiPaymentStatus.PROCESSING);

        var responseXml = service.create(
                payment.paymentId().value().toString(),
                readFixture("camt056-investigation-pending.xml"),
                "application/xml",
                authorizationContext("fi-client-a", "corr-fi-recall-processing"),
                "idem-recall-processing",
                "investigation_pending");

        var recall = recallRepository.findByPaymentId(payment.paymentId()).orElseThrow();
        assertThat(recall.status().name()).isEqualTo("PENDING");
        assertThat(responseXml).contains("PDCR").contains("corr-fi-recall-processing");
    }

    @Test
    void rejectedPaymentRejectsRecall() throws Exception {
        var payment = storePayment("fi-client-a", FiPaymentStatus.REJECTED);

        assertThatThrownBy(() -> service.create(
                        payment.paymentId().value().toString(),
                        readFixture("camt056-recall-accepted.xml"),
                        "text/xml",
                        authorizationContext("fi-client-a", "corr-fi-recall-rejected-payment"),
                        "idem-recall-rejected-payment",
                        "recall_accepted"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Only SETTLED or PROCESSING FI payments can be recalled");
        assertThat(recallRepository.findByPaymentId(payment.paymentId())).isEmpty();
    }

    @Test
    void wrongOriginalReferenceRejectsRecall() throws Exception {
        var payment = storePayment("fi-client-a", FiPaymentStatus.SETTLED);

        assertThatThrownBy(() -> service.create(
                        payment.paymentId().value().toString(),
                        readFixture("camt056-wrong-original-reference.xml"),
                        "application/camt.056+xml",
                        authorizationContext("fi-client-a", "corr-fi-recall-wrong-ref"),
                        "idem-recall-wrong-ref",
                        "recall_accepted"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("camt.056 original payment reference does not match target FI payment");
        assertThat(recallRepository.findByPaymentId(payment.paymentId())).isEmpty();
    }

    @Test
    void unrelatedClientCannotRecallPayment() throws Exception {
        var payment = storePayment("fi-client-a", FiPaymentStatus.SETTLED);

        assertThatThrownBy(() -> service.create(
                        payment.paymentId().value().toString(),
                        readFixture("camt056-recall-accepted.xml"),
                        "application/camt.056+xml",
                        authorizationContext("fi-client-b", "corr-fi-recall-other-client"),
                        "idem-recall-other-client",
                        "recall_accepted"))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThat(recallRepository.findByPaymentId(payment.paymentId())).isEmpty();
    }

    @Test
    void duplicateSameSemanticsAndKeyReplaysOriginalCamt029() throws Exception {
        var payment = storePayment("fi-client-a", FiPaymentStatus.SETTLED);
        var first = service.create(
                payment.paymentId().value().toString(),
                readFixture("camt056-recall-accepted.xml"),
                "application/camt.056+xml",
                authorizationContext("fi-client-a", "corr-fi-recall-first"),
                "idem-recall-replay",
                "recall_accepted");

        var replay = service.create(
                payment.paymentId().value().toString(),
                readFixture("camt056-recall-accepted.xml").replaceAll(">\\s+<", "><"),
                "application/xml",
                authorizationContext("fi-client-a", "corr-fi-recall-new"),
                "idem-recall-replay",
                "recall_accepted");

        assertThat(replay).isEqualTo(first);
        assertThat(recallRepository.findByPaymentId(payment.paymentId())).isPresent();
    }

    @Test
    void sameKeyDifferentSemanticsOrScenarioReturnsConflict() throws Exception {
        var payment = storePayment("fi-client-a", FiPaymentStatus.SETTLED);
        service.create(
                payment.paymentId().value().toString(),
                readFixture("camt056-recall-accepted.xml"),
                "application/camt.056+xml",
                authorizationContext("fi-client-a", "corr-fi-recall-conflict-first"),
                "idem-recall-conflict",
                "recall_accepted");

        assertThatThrownBy(() -> service.create(
                        payment.paymentId().value().toString(),
                        readFixture("camt056-recall-accepted.xml"),
                        "application/camt.056+xml",
                        authorizationContext("fi-client-a", "corr-fi-recall-conflict-second"),
                        "idem-recall-conflict",
                        "recall_rejected"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void secondDifferentRecallForSamePaymentReturnsConflict() throws Exception {
        var payment = storePayment("fi-client-a", FiPaymentStatus.SETTLED);
        service.create(
                payment.paymentId().value().toString(),
                readFixture("camt056-recall-accepted.xml"),
                "application/camt.056+xml",
                authorizationContext("fi-client-a", "corr-fi-recall-first"),
                "idem-recall-first",
                "recall_accepted");

        assertThatThrownBy(() -> service.create(
                        payment.paymentId().value().toString(),
                        readFixture("camt056-recall-rejected.xml"),
                        "application/camt.056+xml",
                        authorizationContext("fi-client-a", "corr-fi-recall-second"),
                        "idem-recall-second",
                        "recall_rejected"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("Recall investigation already exists for FI payment");
    }

    @Test
    void idempotencySaveFailureDoesNotPersistRecallSideEffect() throws Exception {
        var payment = storePayment("fi-client-a", FiPaymentStatus.SETTLED);
        var recallRepository = new InMemoryRecallInvestigationRepository();
        var service = new CreateRecallInvestigationService(
                new Camt056Parser(),
                new DeterministicRecallInvestigationSimulator(),
                new TestRecallInvestigationResponseRenderer(),
                fiPaymentRepository,
                recallRepository,
                new FailingSaveIdempotencyRepository(),
                new RequestFingerprintService());

        assertThatThrownBy(() -> service.create(
                        payment.paymentId().value().toString(),
                        readFixture("camt056-recall-accepted.xml"),
                        "application/camt.056+xml",
                        authorizationContext("fi-client-a", "corr-fi-recall-idem-failure"),
                        "idem-recall-save-failure",
                        "recall_accepted"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("simulated idempotency save failure");
        assertThat(recallRepository.findByPaymentId(payment.paymentId())).isEmpty();
    }

    @Test
    void idempotencyRecordSavedBeforeRecallConflictDoesNotReplayDifferentRecall() throws Exception {
        var payment = storePayment("fi-client-a", FiPaymentStatus.SETTLED);
        var existingRecall = existingRecallFor(payment);
        var recallRepository = new RaceConflictRecallInvestigationRepository(existingRecall);
        var service = new CreateRecallInvestigationService(
                new Camt056Parser(),
                new DeterministicRecallInvestigationSimulator(),
                new TestRecallInvestigationResponseRenderer(),
                fiPaymentRepository,
                recallRepository,
                new InMemoryIdempotencyRepository(),
                new RequestFingerprintService());

        assertThatThrownBy(() -> service.create(
                        payment.paymentId().value().toString(),
                        readFixture("camt056-recall-accepted.xml"),
                        "application/camt.056+xml",
                        authorizationContext("fi-client-a", "corr-fi-recall-race-first"),
                        "idem-recall-race",
                        "recall_accepted"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("Recall investigation already exists for FI payment");

        assertThatThrownBy(() -> service.create(
                        payment.paymentId().value().toString(),
                        readFixture("camt056-recall-accepted.xml"),
                        "application/camt.056+xml",
                        authorizationContext("fi-client-a", "corr-fi-recall-race-replay"),
                        "idem-recall-race",
                        "recall_accepted"))
                .isInstanceOf(IdempotencyConflictException.class)
                .hasMessageContaining("Idempotency record does not match the stored recall investigation");
    }

    private RecallInvestigationRecord existingRecallFor(FiPaymentRecord payment) {
        var now = Instant.parse("2026-05-28T00:00:00Z");
        return new RecallInvestigationRecord(
                new RecallInvestigationId(UUID.randomUUID()),
                payment.paymentId(),
                payment.ownerClientId(),
                "EXISTING-CAMT056",
                "EXISTING-CASE",
                payment.identifiers().originalPaymentReference(),
                RecallInvestigationStatus.ACCEPTED,
                Optional.of("AC01"),
                Optional.of("Existing recall"),
                payment.correspondentSettlementContext(),
                new CorrelationId("corr-existing-recall"),
                now,
                now);
    }

    @Test
    void missingIdempotencyKeyFails() throws Exception {
        var payment = storePayment("fi-client-a", FiPaymentStatus.SETTLED);

        assertThatThrownBy(() -> service.create(
                        payment.paymentId().value().toString(),
                        readFixture("camt056-recall-accepted.xml"),
                        "application/camt.056+xml",
                        authorizationContext("fi-client-a", "corr-fi-recall-missing-idem"),
                        " ",
                        "recall_accepted"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Idempotency-Key is required");
    }

    @Test
    void unsupportedContentTypeFails() throws Exception {
        var payment = storePayment("fi-client-a", FiPaymentStatus.SETTLED);

        assertThatThrownBy(() -> service.create(
                        payment.paymentId().value().toString(),
                        readFixture("camt056-recall-accepted.xml"),
                        "application/json",
                        authorizationContext("fi-client-a", "corr-fi-recall-content-type"),
                        "idem-recall-content-type",
                        "recall_accepted"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsupported camt.056 content type");
    }

    private FiPaymentRecord storePayment(String ownerClientId, FiPaymentStatus status) {
        var now = Instant.parse("2026-05-28T00:00:00Z");
        var record = new FiPaymentRecord(
                new FiPaymentId(UUID.randomUUID()),
                ownerClientId,
                new FiPaymentIdentifiers(
                        "FI-MSG-20260528-0001",
                        "FI-INSTR-20260528-0001",
                        "FI-E2E-20260528-0001"),
                new FiParty("CIBBHKHH"),
                new FiParty("CORRUS33"),
                new Money("USD", "100000.00"),
                "USD",
                status,
                settlementContext(),
                new CorrelationId("corr-fi-payment"),
                now,
                now,
                Optional.ofNullable(status == FiPaymentStatus.REJECTED
                        ? new PaymentReason("FI_REJECTED", "FI payment rejected")
                        : null));
        return fiPaymentRepository.save(record);
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

    private AuthorizationContext authorizationContext(String clientId, String correlationId) {
        return new AuthorizationContext(
                clientId,
                clientId,
                Set.of("fi-payments:investigate"),
                "tenant-a",
                Map.of(),
                Instant.parse("2026-05-28T00:00:00Z"),
                "jwt-id",
                new CorrelationId(correlationId));
    }

    private String readFixture(String fileName) throws Exception {
        return Files.readString(Path.of("src", "test", "resources", "fi", fileName), StandardCharsets.UTF_8);
    }

    private static final class FailingSaveIdempotencyRepository implements IdempotencyRepository {
        @Override
        public Optional<IdempotencyRecord> find(String clientId, String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public IdempotencyRecord saveIfAbsent(IdempotencyRecord record) {
            throw new IllegalStateException("simulated idempotency save failure");
        }
    }

    private static final class RaceConflictRecallInvestigationRepository implements RecallInvestigationRepository {
        private final RecallInvestigationRecord existing;
        private boolean saveAttempted;

        private RaceConflictRecallInvestigationRepository(RecallInvestigationRecord existing) {
            this.existing = existing;
        }

        @Override
        public Optional<RecallInvestigationRecord> findByPaymentId(FiPaymentId paymentId) {
            if (!saveAttempted) {
                return Optional.empty();
            }
            return Optional.of(existing).filter(record -> record.fiPaymentId().equals(paymentId));
        }

        @Override
        public Optional<RecallInvestigationRecord> findByPaymentIdAndOwnerClientId(
                FiPaymentId paymentId,
                String ownerClientId) {
            return findByPaymentId(paymentId).filter(record -> record.ownerClientId().equals(ownerClientId));
        }

        @Override
        public RecallInvestigationRecord saveIfAbsent(RecallInvestigationRecord record) {
            saveAttempted = true;
            return existing;
        }
    }

    private static final class TestRecallInvestigationResponseRenderer implements RecallInvestigationResponseRenderer {
        @Override
        public String render(RecallInvestigationRecord record) {
            return "<camt029 id=\"camt029-" + record.investigationId().value()
                    + "\" statusCode=\"" + confirmationCode(record.status())
                    + "\" correlationId=\"" + record.correlationId().value()
                    + "\"/>";
        }

        private String confirmationCode(RecallInvestigationStatus status) {
            return switch (status) {
                case ACCEPTED -> "CNCL";
                case REJECTED -> "RJCR";
                case PENDING -> "PDCR";
            };
        }
    }
}
