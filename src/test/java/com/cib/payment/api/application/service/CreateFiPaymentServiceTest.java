package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.api.dto.FiPaymentAcknowledgementResponse;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.FiCorrespondentPaymentOutcome;
import com.cib.payment.api.application.port.FiCorrespondentPaymentSimulator;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.CorrespondentSettlementContext;
import com.cib.payment.api.domain.model.FiPaymentCandidate;
import com.cib.payment.api.domain.model.FiPaymentId;
import com.cib.payment.api.domain.model.FiPaymentRecord;
import com.cib.payment.api.domain.model.FiPaymentStatus;
import com.cib.payment.api.infrastructure.iso.Pacs009Parser;
import com.cib.payment.api.infrastructure.persistence.InMemoryFiPaymentRepository;
import com.cib.payment.api.infrastructure.persistence.InMemoryIdempotencyRepository;
import com.cib.payment.api.infrastructure.simulator.DeterministicFiCorrespondentPaymentSimulator;
import com.cib.payment.api.infrastructure.simulator.FiCorrespondentRouteProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CreateFiPaymentServiceTest {
    private final InMemoryFiPaymentRepository fiPaymentRepository = new InMemoryFiPaymentRepository();
    private final CreateFiPaymentService service = new CreateFiPaymentService(
            new FiPaymentAdmissionService(new Pacs009Parser()),
            new FiCorrespondentRouteProfile(),
            new DeterministicFiCorrespondentPaymentSimulator(),
            fiPaymentRepository,
            new InMemoryIdempotencyRepository(),
            new RequestFingerprintService());

    @Test
    void acceptedScenarioStoresSettledAndAcknowledgementHasDerivedContext() throws Exception {
        var response = service.create(
                readFixture("pacs009-accepted-nostro.xml"),
                "application/pacs.009+xml",
                authorizationContext("fi-client-a", "corr-fi-create-1"),
                "idem-fi-accepted",
                "fi_payment_accepted");

        var record = fiPaymentRepository.findById(fiPaymentId(response.paymentId())).orElseThrow();
        assertThat(record.status()).isEqualTo(FiPaymentStatus.SETTLED);
        assertThat(response.paymentId()).isEqualTo(record.paymentId().value().toString());
        assertThat(response.status()).isEqualTo("SETTLED");
        assertThat(response.correlationId()).isEqualTo("corr-fi-create-1");
        assertThat(response.statusLink()).isEqualTo("/v1/fi-payments/" + response.paymentId());
        assertThat(response.reason()).isNull();
        assertThat(response.correspondentSettlementContext().instructingAgentBic()).isEqualTo("CIBBHKHH");
        assertThat(response.correspondentSettlementContext().instructedAgentBic()).isEqualTo("CORRUS33");
        assertThat(response.correspondentSettlementContext().correspondentOrIntermediaryBic()).isEqualTo("CORRUS33");
        assertThat(response.correspondentSettlementContext().settlementCurrency()).isEqualTo("USD");
        assertThat(response.correspondentSettlementContext().accountRelationshipRole()).isEqualTo("NOSTRO");
        assertThat(response.correspondentSettlementContext().maskedSimulatedAccountReference())
                .isEqualTo("nostro-usd-corrus33-****1234");
    }

    @Test
    void pendingScenarioStoresProcessing() throws Exception {
        var response = service.create(
                readFixture("pacs009-pending-vostro.xml"),
                "application/xml",
                authorizationContext("fi-client-a", "corr-fi-create-2"),
                "idem-fi-pending",
                "fi_payment_pending_correspondent_review");

        var record = fiPaymentRepository.findById(fiPaymentId(response.paymentId())).orElseThrow();
        assertThat(record.status()).isEqualTo(FiPaymentStatus.PROCESSING);
        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.reason().code()).isEqualTo("FI_CORRESPONDENT_REVIEW");
    }

    @Test
    void rejectedScenarioStoresRejected() throws Exception {
        var response = service.create(
                readFixture("pacs009-rejected-loro.xml"),
                "text/xml",
                authorizationContext("fi-client-a", "corr-fi-create-3"),
                "idem-fi-rejected",
                "fi_payment_rejected_unsupported_correspondent");

        var record = fiPaymentRepository.findById(fiPaymentId(response.paymentId())).orElseThrow();
        assertThat(record.status()).isEqualTo(FiPaymentStatus.REJECTED);
        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.reason().code()).isEqualTo("FI_UNSUPPORTED_CORRESPONDENT");
    }

    @Test
    void routeValidationCatchesUnsupportedRouteInProductionCreateFlow() throws Exception {
        assertThatThrownBy(() -> service.create(
                        readFixture("pacs009-accepted-nostro.xml").replace("CORRUS33", "UNKNOWN33"),
                        "application/pacs.009+xml",
                        authorizationContext("fi-client-a", "corr-fi-create-4"),
                        "idem-fi-unsupported-route",
                        "fi_payment_accepted"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsupported FI correspondent route");
    }

    @Test
    void missingIdempotencyKeyFails() throws Exception {
        assertThatThrownBy(() -> service.create(
                        readFixture("pacs009-accepted-nostro.xml"),
                        "application/pacs.009+xml",
                        authorizationContext("fi-client-a", "corr-fi-create-5"),
                        " ",
                        "fi_payment_accepted"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Idempotency-Key is required");
    }

    @Test
    void duplicateSameRequestReplaysOriginalAcknowledgement() throws Exception {
        var first = service.create(
                readFixture("pacs009-accepted-nostro.xml"),
                "application/pacs.009+xml",
                authorizationContext("fi-client-a", "corr-fi-create-6"),
                "idem-fi-replay",
                "fi_payment_accepted");

        var replay = service.create(
                readFixture("pacs009-accepted-nostro.xml").replaceAll(">\\s+<", "><"),
                "application/xml",
                authorizationContext("fi-client-a", "corr-fi-create-replay-new"),
                "idem-fi-replay",
                "fi_payment_accepted");

        assertThat(replay).isEqualTo(first);
        assertThat(fiPaymentRepository.findById(fiPaymentId(first.paymentId()))).isPresent();
    }

    @Test
    void sameKeyDifferentSemanticsOrScenarioReturnsConflict() throws Exception {
        service.create(
                readFixture("pacs009-accepted-nostro.xml"),
                "application/pacs.009+xml",
                authorizationContext("fi-client-a", "corr-fi-create-7"),
                "idem-fi-conflict",
                "fi_payment_accepted");

        assertThatThrownBy(() -> service.create(
                        readFixture("pacs009-accepted-nostro.xml"),
                        "application/pacs.009+xml",
                        authorizationContext("fi-client-a", "corr-fi-create-8"),
                        "idem-fi-conflict",
                        "fi_payment_pending_correspondent_review"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void concurrentEquivalentRequestsWithSameIdempotencyKeyReplayOneAcknowledgement() throws Exception {
        var repository = new CountingFiPaymentRepository();
        var simulator = new CountingFiCorrespondentPaymentSimulator();
        var concurrentService = new CreateFiPaymentService(
                new FiPaymentAdmissionService(new Pacs009Parser()),
                new FiCorrespondentRouteProfile(),
                simulator,
                repository,
                new InMemoryIdempotencyRepository(),
                new RequestFingerprintService());
        var callers = 16;
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(callers);
        var tasks = new ArrayList<Callable<FiPaymentAcknowledgementResponse>>();
        for (int i = 0; i < callers; i++) {
            tasks.add(() -> {
                start.await(5, TimeUnit.SECONDS);
                return concurrentService.create(
                        readFixture("pacs009-accepted-nostro.xml").replaceAll(">\\s+<", "><"),
                        "application/xml",
                        authorizationContext("fi-client-concurrent", "corr-fi-concurrent"),
                        "same-fi-key",
                        "fi_payment_accepted");
            });
        }

        try {
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();

            var responses = new ArrayList<FiPaymentAcknowledgementResponse>();
            for (var future : futures) {
                responses.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(responses).containsOnly(responses.getFirst());
            assertThat(repository.saves()).isEqualTo(1);
            assertThat(simulator.invocations()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private String readFixture(String fileName) throws Exception {
        return Files.readString(Path.of("src", "test", "resources", "fi", fileName), StandardCharsets.UTF_8);
    }

    private AuthorizationContext authorizationContext(String clientId, String correlationId) {
        return new AuthorizationContext(
                clientId,
                clientId,
                Set.of("payments:create", "payments:read"),
                "tenant-a",
                Map.of(),
                Instant.parse("2026-05-28T00:00:00Z"),
                "jwt-id",
                new CorrelationId(correlationId));
    }

    private FiPaymentId fiPaymentId(String paymentId) {
        return new FiPaymentId(UUID.fromString(paymentId));
    }

    private static class CountingFiPaymentRepository extends InMemoryFiPaymentRepository {
        private final AtomicInteger saves = new AtomicInteger();

        @Override
        public FiPaymentRecord save(FiPaymentRecord record) {
            saves.incrementAndGet();
            return super.save(record);
        }

        int saves() {
            return saves.get();
        }
    }

    private static class CountingFiCorrespondentPaymentSimulator implements FiCorrespondentPaymentSimulator {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public FiCorrespondentPaymentOutcome process(
                FiPaymentCandidate candidate,
                CorrespondentSettlementContext settlementContext,
                String scenarioContext) {
            invocations.incrementAndGet();
            sleep();
            return new FiCorrespondentPaymentOutcome(FiPaymentStatus.SETTLED, Optional.empty());
        }

        int invocations() {
            return invocations.get();
        }

        private void sleep() {
            try {
                Thread.sleep(100);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
