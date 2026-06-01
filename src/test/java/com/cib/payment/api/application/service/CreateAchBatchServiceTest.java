package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.api.dto.AccountReferenceRequest;
import com.cib.payment.api.api.dto.AchBatchEntryRequest;
import com.cib.payment.api.api.dto.CreateAchBatchRequest;
import com.cib.payment.api.api.dto.MoneyRequest;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.domain.model.AchBatchId;
import com.cib.payment.api.domain.model.AchBatchStatus;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.infrastructure.persistence.InMemoryAchBatchRepository;
import com.cib.payment.api.infrastructure.persistence.InMemoryIdempotencyRepository;
import com.cib.payment.api.infrastructure.simulator.DeterministicAchDirectCreditSimulator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateAchBatchServiceTest {
    private final InMemoryAchBatchRepository achBatchRepository = new InMemoryAchBatchRepository();
    private final CapturingIdempotencyRepository idempotencyRepository = new CapturingIdempotencyRepository();
    private final CreateAchBatchService service = new CreateAchBatchService(
            achBatchRepository,
            idempotencyRepository,
            new RequestFingerprintService(),
            new DeterministicAchDirectCreditSimulator());

    @Test
    void createsAcceptedAchBatchAndStoresOriginalJsonResponseForReplay() {
        var response = service.create(
                validRequest(),
                authorizationContext("ach-client-a", "corr-ach-service-create"),
                "idem-ach-create",
                "ach_direct_credit_accepted");

        var batchId = new AchBatchId(UUID.fromString(response.batchId()));
        var record = achBatchRepository.find(batchId).orElseThrow();
        assertThat(record.status()).isEqualTo(AchBatchStatus.ACCEPTED_FOR_CLEARING);
        assertThat(record.clientId()).isEqualTo("ach-client-a");
        assertThat(record.correlationId().value()).isEqualTo("corr-ach-service-create");
        assertThat(response.status()).isEqualTo("ACCEPTED_FOR_CLEARING");
        assertThat(response.correlationId()).isEqualTo("corr-ach-service-create");
        assertThat(response.links().status()).isEqualTo("/v1/ach-batches/" + response.batchId());
        assertThat(idempotencyRepository.lastSaved().orElseThrow().originalResponseJson())
                .contains(response.batchId(), "corr-ach-service-create");
    }

    @Test
    void duplicateSameRequestAndScenarioReplaysOriginalResponse() {
        var first = service.create(
                validRequest(),
                authorizationContext("ach-client-a", "corr-ach-original"),
                "idem-ach-replay",
                "ach_direct_credit_accepted");

        var replay = service.create(
                validRequest(),
                authorizationContext("ach-client-a", "corr-ach-replay"),
                "idem-ach-replay",
                "ach_direct_credit_accepted");

        assertThat(replay).isEqualTo(first);
        assertThat(replay.correlationId()).isEqualTo("corr-ach-original");
    }

    @Test
    void duplicateSameKeyDifferentBodyOrScenarioConflicts() {
        service.create(
                validRequest(),
                authorizationContext("ach-client-a", "corr-ach-first"),
                "idem-ach-conflict",
                "ach_direct_credit_accepted");

        assertThatThrownBy(() -> service.create(
                        requestWithEntryReference("ACH-ENTRY-CHANGED"),
                        authorizationContext("ach-client-a", "corr-ach-different-body"),
                        "idem-ach-conflict",
                        "ach_direct_credit_accepted"))
                .isInstanceOf(IdempotencyConflictException.class);

        assertThatThrownBy(() -> service.create(
                        validRequest(),
                        authorizationContext("ach-client-a", "corr-ach-different-scenario"),
                        "idem-ach-conflict",
                        "ach_direct_credit_partially_returned"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void missingIdempotencyKeyAndInvalidAchRulesFailValidation() {
        assertThatThrownBy(() -> service.create(
                        validRequest(),
                        authorizationContext("ach-client-a", "corr-ach-missing-idem"),
                        " ",
                        "ach_direct_credit_accepted"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Idempotency-Key is required");

        assertThatThrownBy(() -> service.create(
                        duplicateEntryReferenceRequest(),
                        authorizationContext("ach-client-a", "corr-ach-duplicate-entry"),
                        "idem-ach-duplicate-entry",
                        "ach_direct_credit_accepted"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Duplicate ACH entryReference");

        assertThatThrownBy(() -> service.create(
                        requestWithCurrency("HKD"),
                        authorizationContext("ach-client-a", "corr-ach-non-usd"),
                        "idem-ach-non-usd",
                        "ach_direct_credit_accepted"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("ACH direct credit entries must be denominated in USD");
    }

    @Test
    void partiallyReturnedScenarioReturnsBatchAndEntryStatuses() {
        var response = service.create(
                validRequest(),
                authorizationContext("ach-client-a", "corr-ach-partial"),
                "idem-ach-partial",
                "ach_direct_credit_partially_returned");

        assertThat(response.status()).isEqualTo("PARTIALLY_RETURNED");
        assertThat(response.reason().code()).isEqualTo("ACH_PARTIAL_RETURN");
        assertThat(response.entries()).hasSize(2);
        assertThat(response.entries().get(0).status()).isEqualTo("RETURNED");
        assertThat(response.entries().get(0).reason().code()).isEqualTo("ACH_ENTRY_RETURNED");
        assertThat(response.entries().get(1).status()).isEqualTo("SETTLED");
    }

    private CreateAchBatchRequest validRequest() {
        return new CreateAchBatchRequest(
                "ACH-BATCH-20260601-0001",
                "CIB Payroll Services",
                LocalDate.parse("2026-06-02"),
                settlementAccount(),
                java.util.List.of(
                        entry("ACH-ENTRY-0001", "USD", "125.40"),
                        entry("ACH-ENTRY-0002", "USD", "225.10")));
    }

    private CreateAchBatchRequest requestWithEntryReference(String entryReference) {
        return new CreateAchBatchRequest(
                "ACH-BATCH-20260601-0001",
                "CIB Payroll Services",
                LocalDate.parse("2026-06-02"),
                settlementAccount(),
                java.util.List.of(
                        entry(entryReference, "USD", "125.40"),
                        entry("ACH-ENTRY-0002", "USD", "225.10")));
    }

    private CreateAchBatchRequest duplicateEntryReferenceRequest() {
        return new CreateAchBatchRequest(
                "ACH-BATCH-20260601-0001",
                "CIB Payroll Services",
                LocalDate.parse("2026-06-02"),
                settlementAccount(),
                java.util.List.of(
                        entry("ACH-ENTRY-DUP", "USD", "125.40"),
                        entry("ACH-ENTRY-DUP", "USD", "225.10")));
    }

    private CreateAchBatchRequest requestWithCurrency(String currency) {
        return new CreateAchBatchRequest(
                "ACH-BATCH-20260601-0001",
                "CIB Payroll Services",
                LocalDate.parse("2026-06-02"),
                settlementAccount(),
                java.util.List.of(entry("ACH-ENTRY-0001", currency, "125.40")));
    }

    private AccountReferenceRequest settlementAccount() {
        return new AccountReferenceRequest("021000021", "123456789", "CIB Operating");
    }

    private AchBatchEntryRequest entry(String entryReference, String currency, String value) {
        return new AchBatchEntryRequest(
                entryReference,
                "Receiver " + entryReference,
                new AccountReferenceRequest("111000025", "987654321", "Receiver Account"),
                new MoneyRequest(currency, value),
                "PAYROLL");
    }

    private AuthorizationContext authorizationContext(String clientId, String correlationId) {
        return new AuthorizationContext(
                clientId,
                clientId,
                Set.of("ach-batches:create", "ach-batches:read"),
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
            var stored = super.saveIfAbsent(record);
            if (stored.equals(record)) {
                lastSaved = record;
            }
            return stored;
        }

        Optional<IdempotencyRecord> lastSaved() {
            return Optional.ofNullable(lastSaved);
        }
    }
}
