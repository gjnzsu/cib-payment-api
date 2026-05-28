package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
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
import java.util.Map;
import java.util.Set;
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

        var record = fiPaymentRepository.findById(response.paymentId()).orElseThrow();
        assertThat(record.status()).isEqualTo(FiPaymentStatus.SETTLED);
        assertThat(response.status()).isEqualTo("SETTLED");
        assertThat(response.correlationId()).isEqualTo("corr-fi-create-1");
        assertThat(response.statusLink()).isEqualTo("/v1/fi-payments/" + response.paymentId().value());
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

        var record = fiPaymentRepository.findById(response.paymentId()).orElseThrow();
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

        var record = fiPaymentRepository.findById(response.paymentId()).orElseThrow();
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
        assertThat(fiPaymentRepository.findById(first.paymentId())).isPresent();
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
}
