package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.api.dto.AccountReferenceRequest;
import com.cib.payment.api.api.dto.CreateMandateRequest;
import com.cib.payment.api.api.dto.MoneyRequest;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.MandateId;
import com.cib.payment.api.domain.model.MandateStatus;
import com.cib.payment.api.infrastructure.persistence.InMemoryIdempotencyRepository;
import com.cib.payment.api.infrastructure.persistence.InMemoryMandateRepository;
import com.cib.payment.api.infrastructure.simulator.DeterministicMandateSimulator;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateMandateServiceTest {
    private final InMemoryMandateRepository mandateRepository = new InMemoryMandateRepository();
    private final CreateMandateService service = new CreateMandateService(
            mandateRepository,
            new InMemoryIdempotencyRepository(),
            new RequestFingerprintService(),
            new DeterministicMandateSimulator());

    @Test
    void createsActiveMandateAndStoresRecordForStatusQuery() {
        var response = service.create(
                request("US_ACH_DEBIT_MANDATE", "MANDATE-US-001", "USD"),
                authorizationContext("mandate-client-a", "corr-mandate-create"),
                "idem-mandate-create",
                "mandate_active");

        var record = mandateRepository.find(new MandateId(UUID.fromString(response.mandateId()))).orElseThrow();
        assertThat(record.clientId()).isEqualTo("mandate-client-a");
        assertThat(record.status()).isEqualTo(MandateStatus.ACTIVE);
        assertThat(record.mandateReference()).isEqualTo("MANDATE-US-001");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.links().status()).isEqualTo("/v1/mandates/" + response.mandateId());
        assertThat(response.correlationId()).isEqualTo("corr-mandate-create");
    }

    @Test
    void pendingRejectedExpiredTimeoutAndFailureScenariosAreStored() {
        assertThat(createWithScenario("mandate_pending_authorization", "MANDATE-PENDING").status())
                .isEqualTo("PENDING_AUTHORIZATION");
        assertThat(createWithScenario("mandate_rejected_by_payer", "MANDATE-REJECTED").status())
                .isEqualTo("REJECTED");
        assertThat(createWithScenario("mandate_expired", "MANDATE-EXPIRED").status())
                .isEqualTo("EXPIRED");
        assertThat(createWithScenario("mandate_timeout", "MANDATE-TIMEOUT").status())
                .isEqualTo("TIMEOUT");
        assertThat(createWithScenario("mandate_internal_failure", "MANDATE-FAILED").status())
                .isEqualTo("FAILED");
    }

    @Test
    void duplicateSameMandateRequestReplaysOriginalResponseAndChangedRequestConflicts() {
        var first = service.create(
                request("HK_FPS_EDDA", "EDDA-HK-001", "HKD"),
                authorizationContext("mandate-client-a", "corr-mandate-original"),
                "idem-mandate-replay",
                "mandate_active");

        var replay = service.create(
                request("HK_FPS_EDDA", "EDDA-HK-001", "HKD"),
                authorizationContext("mandate-client-a", "corr-mandate-replay"),
                "idem-mandate-replay",
                "mandate_active");

        assertThat(replay).isEqualTo(first);
        assertThat(replay.correlationId()).isEqualTo("corr-mandate-original");

        assertThatThrownBy(() -> service.create(
                        request("HK_FPS_EDDA", "EDDA-HK-CHANGED", "HKD"),
                        authorizationContext("mandate-client-a", "corr-mandate-conflict"),
                        "idem-mandate-replay",
                        "mandate_active"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void duplicateMandateReferenceForSameClientFailsValidation() {
        service.create(
                request("US_ACH_DEBIT_MANDATE", "MANDATE-DUPLICATE", "USD"),
                authorizationContext("mandate-client-a", "corr-mandate-duplicate-first"),
                "idem-mandate-duplicate-first",
                "mandate_active");

        assertThatThrownBy(() -> service.create(
                        request("US_ACH_DEBIT_MANDATE", "MANDATE-DUPLICATE", "USD"),
                        authorizationContext("mandate-client-a", "corr-mandate-duplicate-second"),
                        "idem-mandate-duplicate-second",
                        "mandate_pending_authorization"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("mandateReference already exists");
    }

    @Test
    void missingIdempotencyUnsupportedProfileAndCurrencyFailValidation() {
        assertThatThrownBy(() -> service.create(
                        request("US_ACH_DEBIT_MANDATE", "MANDATE-US-001", "USD"),
                        authorizationContext("mandate-client-a", "corr-mandate-missing-idem"),
                        " ",
                        "mandate_active"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Idempotency-Key is required");

        assertThatThrownBy(() -> service.create(
                        request("UNSUPPORTED", "MANDATE-US-001", "USD"),
                        authorizationContext("mandate-client-a", "corr-mandate-profile"),
                        "idem-mandate-profile",
                        "mandate_active"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsupported mandateProfile");

        assertThatThrownBy(() -> service.create(
                        request("HK_FPS_EDDA", "EDDA-HK-001", "USD"),
                        authorizationContext("mandate-client-a", "corr-mandate-currency"),
                        "idem-mandate-currency",
                        "mandate_active"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("HK FPS eDDA mandates must be denominated in HKD");
    }

    private com.cib.payment.api.api.dto.MandateResponse createWithScenario(String scenario, String reference) {
        return service.create(
                request("US_ACH_DEBIT_MANDATE", reference, "USD"),
                authorizationContext("mandate-client-a", "corr-" + reference),
                "idem-" + reference,
                scenario);
    }

    private CreateMandateRequest request(String profile, String mandateReference, String currency) {
        return new CreateMandateRequest(
                profile,
                mandateReference,
                "CIB Collection Services",
                "Corporate Customer",
                new AccountReferenceRequest("021000021", "123456789", "CIB Collections"),
                new AccountReferenceRequest("111000025", "987654321", "Corporate Customer"),
                new MoneyRequest(currency, "1000.00"),
                "VARIABLE",
                "MONTHLY_COLLECTION");
    }

    private AuthorizationContext authorizationContext(String clientId, String correlationId) {
        return new AuthorizationContext(
                clientId,
                clientId,
                Set.of("mandates:create", "mandates:read", "mandates:cancel"),
                "tenant-a",
                Map.of(),
                Instant.parse("2026-07-08T00:00:00Z"),
                "jwt-id",
                new CorrelationId(correlationId));
    }
}
