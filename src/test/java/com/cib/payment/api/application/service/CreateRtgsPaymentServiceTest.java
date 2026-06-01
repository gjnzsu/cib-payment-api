package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.api.dto.AccountReferenceRequest;
import com.cib.payment.api.api.dto.CreateRtgsPaymentRequest;
import com.cib.payment.api.api.dto.MoneyRequest;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.RtgsPaymentOutcome;
import com.cib.payment.api.application.port.RtgsPaymentSimulator;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.RtgsPaymentId;
import com.cib.payment.api.domain.model.RtgsPaymentRecord;
import com.cib.payment.api.domain.model.RtgsPaymentStatus;
import com.cib.payment.api.infrastructure.persistence.InMemoryIdempotencyRepository;
import com.cib.payment.api.infrastructure.persistence.InMemoryRtgsPaymentRepository;
import com.cib.payment.api.infrastructure.simulator.DeterministicRtgsPaymentSimulator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CreateRtgsPaymentServiceTest {
    private final CapturingIdempotencyRepository idempotencyRepository = new CapturingIdempotencyRepository();
    private final InMemoryRtgsPaymentRepository rtgsPaymentRepository = new InMemoryRtgsPaymentRepository();
    private final CreateRtgsPaymentService service = new CreateRtgsPaymentService(
            rtgsPaymentRepository,
            idempotencyRepository,
            new RequestFingerprintService(),
            new DeterministicRtgsPaymentSimulator());

    @Test
    void corporateSettledScenarioStoresFinalPaymentAndOriginalResponseJson() {
        var response = service.create(
                corporateRequest(),
                authorizationContext("client-a", "corr-rtgs-corp"),
                "idem-rtgs-corp",
                "rtgs_settled");

        var record = rtgsPaymentRepository.find(new RtgsPaymentId(UUID.fromString(response.paymentId()))).orElseThrow();
        assertThat(record.status()).isEqualTo(RtgsPaymentStatus.SETTLED);
        assertThat(record.settlementFinality()).isTrue();
        assertThat(record.ownerClientId()).isEqualTo("client-a");
        assertThat(response.rail()).isEqualTo("RTGS");
        assertThat(response.clientSegment()).isEqualTo("CORPORATE");
        assertThat(response.status()).isEqualTo("SETTLED");
        assertThat(response.settlementFinality()).isTrue();
        assertThat(response.correlationId()).isEqualTo("corr-rtgs-corp");
        assertThat(response.links().status()).isEqualTo("/v1/rtgs-payments/" + response.paymentId());
        assertThat(idempotencyRepository.lastSaved().orElseThrow().correlationId().value())
                .isEqualTo("corr-rtgs-corp");
        assertThat(idempotencyRepository.lastSaved().orElseThrow().originalResponseJson())
                .contains("\"clientSegment\":\"CORPORATE\"", "\"settlementFinality\":true");
    }

    @Test
    void fiSettledAndLiquidityQueueScenariosExposeSegmentFinalityAndReason() {
        var settled = service.create(
                fiRequest(),
                authorizationContext("fi-client-a", "corr-rtgs-fi-settled"),
                "idem-rtgs-fi-settled",
                "rtgs_settled");
        assertThat(settled.clientSegment()).isEqualTo("FI");
        assertThat(settled.status()).isEqualTo("SETTLED");
        assertThat(settled.settlementFinality()).isTrue();

        var queued = service.create(
                fiRequest(),
                authorizationContext("fi-client-a", "corr-rtgs-fi-queued"),
                "idem-rtgs-fi-queued",
                "rtgs_queued_for_liquidity");
        assertThat(queued.clientSegment()).isEqualTo("FI");
        assertThat(queued.status()).isEqualTo("QUEUED_FOR_LIQUIDITY");
        assertThat(queued.settlementFinality()).isFalse();
        assertThat(queued.reason().code()).isEqualTo("RTGS_LIQUIDITY_QUEUE");
    }

    @Test
    void rejectedScenarioStoresRejectedWithoutSettlementFinality() {
        var response = service.create(
                corporateRequest(),
                authorizationContext("client-a", "corr-rtgs-rejected"),
                "idem-rtgs-rejected",
                "rtgs_rejected");

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.settlementFinality()).isFalse();
        assertThat(response.reason().code()).isEqualTo("RTGS_PAYMENT_REJECTED");
    }

    @Test
    void validatesRtgsBusinessRulesWithoutHighValueThreshold() {
        assertThatThrownBy(() -> service.create(
                        corporateRequest(null, account(), new MoneyRequest("USD", "1.00")),
                        authorizationContext("client-a", "corr-rtgs-invalid-corp"),
                        "idem-rtgs-invalid-corp",
                        "rtgs_settled"))
                .isInstanceOf(ValidationFailureException.class);

        assertThatThrownBy(() -> service.create(
                        new CreateRtgsPaymentRequest(
                                "RTGS-2026-0002",
                                "FI",
                                null,
                                null,
                                " ",
                                "IRVTUS3NXXX",
                                new MoneyRequest("USD", "1.00"),
                                LocalDate.of(2026, 6, 5),
                                "NORMAL",
                                "FI settlement"),
                        authorizationContext("fi-client-a", "corr-rtgs-invalid-fi"),
                        "idem-rtgs-invalid-fi",
                        "rtgs_settled"))
                .isInstanceOf(ValidationFailureException.class);

        assertThatThrownBy(() -> service.create(
                        corporateRequest(account(), account(), new MoneyRequest("EUR", "1.00")),
                        authorizationContext("client-a", "corr-rtgs-invalid-currency"),
                        "idem-rtgs-invalid-currency",
                        "rtgs_settled"))
                .isInstanceOf(ValidationFailureException.class);

        var response = service.create(
                corporateRequest(account(), account(), new MoneyRequest("USD", "1.00")),
                authorizationContext("client-a", "corr-rtgs-low-value"),
                "idem-rtgs-low-value",
                "rtgs_settled");
        assertThat(response.status()).isEqualTo("SETTLED");
    }

    @Test
    void sameClientSameKeySameBodyReplaysOriginalResponseButScenarioChangeConflicts() {
        var first = service.create(
                corporateRequest(),
                authorizationContext("client-a", "corr-rtgs-original"),
                "idem-rtgs-replay",
                "rtgs_settled");

        var replay = service.create(
                corporateRequest(),
                authorizationContext("client-a", "corr-rtgs-replay"),
                "idem-rtgs-replay",
                "rtgs_settled");
        assertThat(replay).isEqualTo(first);

        assertThatThrownBy(() -> service.create(
                        corporateRequest(),
                        authorizationContext("client-a", "corr-rtgs-conflict"),
                        "idem-rtgs-replay",
                        "rtgs_rejected"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void defaultScenarioIsSettledAndFiRtgsDoesNotUseFiCorrespondentSimulator() {
        var simulator = new CountingRtgsPaymentSimulator();
        var service = new CreateRtgsPaymentService(
                new InMemoryRtgsPaymentRepository(),
                new InMemoryIdempotencyRepository(),
                new RequestFingerprintService(),
                simulator);

        var response = service.create(
                fiRequest(),
                authorizationContext("fi-client-a", "corr-rtgs-default"),
                "idem-rtgs-default",
                null);

        assertThat(response.clientSegment()).isEqualTo("FI");
        assertThat(response.status()).isEqualTo("SETTLED");
        assertThat(simulator.scenario()).isEqualTo("rtgs_settled");
        assertThat(simulator.invocations()).isEqualTo(1);
    }

    @Test
    void missingIdempotencyKeyFails() {
        assertThatThrownBy(() -> service.create(
                        corporateRequest(),
                        authorizationContext("client-a", "corr-rtgs-missing-idem"),
                        " ",
                        "rtgs_settled"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Idempotency-Key is required");
    }

    private CreateRtgsPaymentRequest corporateRequest() {
        return corporateRequest(account(), account(), new MoneyRequest("USD", "1250.50"));
    }

    private CreateRtgsPaymentRequest corporateRequest(
            AccountReferenceRequest debtor,
            AccountReferenceRequest creditor,
            MoneyRequest amount) {
        return new CreateRtgsPaymentRequest(
                "RTGS-2026-0001",
                "CORPORATE",
                debtor,
                creditor,
                null,
                null,
                amount,
                LocalDate.of(2026, 6, 5),
                "URGENT",
                "Treasury transfer");
    }

    private CreateRtgsPaymentRequest fiRequest() {
        return new CreateRtgsPaymentRequest(
                "RTGS-2026-0002",
                "FI",
                null,
                null,
                "CITIUS33XXX",
                "IRVTUS3NXXX",
                new MoneyRequest("USD", "5000000.00"),
                LocalDate.of(2026, 6, 5),
                "NORMAL",
                "FI settlement");
    }

    private AccountReferenceRequest account() {
        return new AccountReferenceRequest("CITIUS33", "123456789", "Acme Operating");
    }

    private AuthorizationContext authorizationContext(String clientId, String correlationId) {
        return new AuthorizationContext(
                clientId,
                clientId,
                Set.of("rtgs-payments:create", "rtgs-payments:read"),
                "tenant-a",
                Map.of(),
                Instant.parse("2026-06-01T00:00:00Z"),
                "jwt-id",
                new CorrelationId(correlationId));
    }

    private static class CapturingIdempotencyRepository extends InMemoryIdempotencyRepository {
        private IdempotencyRecord lastSaved;

        @Override
        public IdempotencyRecord saveIfAbsent(IdempotencyRecord record) {
            lastSaved = super.saveIfAbsent(record);
            return lastSaved;
        }

        Optional<IdempotencyRecord> lastSaved() {
            return Optional.ofNullable(lastSaved);
        }
    }

    private static class CountingRtgsPaymentSimulator implements RtgsPaymentSimulator {
        private final AtomicInteger invocations = new AtomicInteger();
        private String scenario;

        @Override
        public RtgsPaymentOutcome process(
                RtgsPaymentRecord acceptedRecord,
                AuthorizationContext authorizationContext,
                String scenario) {
            invocations.incrementAndGet();
            this.scenario = scenario;
            return new RtgsPaymentOutcome(
                    RtgsPaymentStatus.SETTLED,
                    true,
                    Optional.<PaymentReason>empty());
        }

        int invocations() {
            return invocations.get();
        }

        String scenario() {
            return scenario;
        }
    }
}
