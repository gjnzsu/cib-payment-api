package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.api.dto.AccountReferenceRequest;
import com.cib.payment.api.api.dto.CollectionEntryRequest;
import com.cib.payment.api.api.dto.CreateCollectionRequest;
import com.cib.payment.api.api.dto.MoneyRequest;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CollectionId;
import com.cib.payment.api.domain.model.CollectionStatus;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.infrastructure.persistence.InMemoryCollectionRepository;
import com.cib.payment.api.infrastructure.persistence.InMemoryIdempotencyRepository;
import com.cib.payment.api.infrastructure.simulator.DeterministicCollectionSimulator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CreateCollectionServiceTest {
    private final InMemoryCollectionRepository collectionRepository = new InMemoryCollectionRepository();
    private final CapturingIdempotencyRepository idempotencyRepository = new CapturingIdempotencyRepository();
    private final CreateCollectionService service = new CreateCollectionService(
            collectionRepository,
            idempotencyRepository,
            new RequestFingerprintService(),
            new DeterministicCollectionSimulator());

    @Test
    void createsUsAchDebitCollectionAndStoresOriginalJsonResponseForReplay() {
        var response = service.create(
                usAchDebitRequest(),
                authorizationContext("collection-client-a", "corr-collection-us-ach"),
                "idem-collection-us-ach",
                "us_ach_debit_collected");

        var collectionId = new CollectionId(UUID.fromString(response.collectionId()));
        var record = collectionRepository.find(collectionId).orElseThrow();
        assertThat(record.collectionProfile().name()).isEqualTo("US_ACH_DIRECT_DEBIT_BATCH");
        assertThat(record.status()).isEqualTo(CollectionStatus.COLLECTED);
        assertThat(record.clientId()).isEqualTo("collection-client-a");
        assertThat(record.correlationId().value()).isEqualTo("corr-collection-us-ach");
        assertThat(response.status()).isEqualTo("COLLECTED");
        assertThat(response.entryCount()).isEqualTo(2);
        assertThat(response.entries()).hasSize(2);
        assertThat(response.links().status()).isEqualTo("/v1/collections/" + response.collectionId());
        assertThat(idempotencyRepository.lastSaved().orElseThrow().originalResponseJson())
                .contains(response.collectionId(), "corr-collection-us-ach");
    }

    @Test
    void createsHkFpsCollectionWithCompletedStatus() {
        var response = service.create(
                hkFpsRequest(),
                authorizationContext("collection-client-a", "corr-collection-hk"),
                "idem-collection-hk",
                "hk_fps_collection_completed");

        var collectionId = new CollectionId(UUID.fromString(response.collectionId()));
        var record = collectionRepository.find(collectionId).orElseThrow();
        assertThat(record.collectionProfile().name()).isEqualTo("HK_FPS_DIRECT_DEBIT");
        assertThat(record.status()).isEqualTo(CollectionStatus.COMPLETED);
        assertThat(record.entryCount()).isZero();
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.totalAmount().currency()).isEqualTo("HKD");
        assertThat(response.correlationId()).isEqualTo("corr-collection-hk");
    }

    @Test
    void duplicateSameCollectionRequestAndScenarioReplaysOriginalResponse() {
        var first = service.create(
                usAchDebitRequest(),
                authorizationContext("collection-client-a", "corr-collection-original"),
                "idem-collection-replay",
                "us_ach_debit_collected");

        var replay = service.create(
                usAchDebitRequest(),
                authorizationContext("collection-client-a", "corr-collection-replay"),
                "idem-collection-replay",
                "us_ach_debit_collected");

        assertThat(replay).isEqualTo(first);
        assertThat(replay.correlationId()).isEqualTo("corr-collection-original");
    }

    @Test
    void duplicateSameKeyDifferentBodyOrScenarioConflicts() {
        service.create(
                usAchDebitRequest(),
                authorizationContext("collection-client-a", "corr-collection-first"),
                "idem-collection-conflict",
                "us_ach_debit_collected");

        assertThatThrownBy(() -> service.create(
                        usAchDebitRequestWithEntryReference("COLL-ENTRY-CHANGED"),
                        authorizationContext("collection-client-a", "corr-collection-different-body"),
                        "idem-collection-conflict",
                        "us_ach_debit_collected"))
                .isInstanceOf(IdempotencyConflictException.class);

        assertThatThrownBy(() -> service.create(
                        usAchDebitRequest(),
                        authorizationContext("collection-client-a", "corr-collection-different-scenario"),
                        "idem-collection-conflict",
                        "us_ach_debit_partially_returned"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void missingIdempotencyDuplicateEntriesAndUnsupportedCurrencyFailValidation() {
        assertThatThrownBy(() -> service.create(
                        usAchDebitRequest(),
                        authorizationContext("collection-client-a", "corr-collection-missing-idem"),
                        " ",
                        "us_ach_debit_collected"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Idempotency-Key is required");

        assertThatThrownBy(() -> service.create(
                        usAchDebitDuplicateEntryReferenceRequest(),
                        authorizationContext("collection-client-a", "corr-collection-duplicate"),
                        "idem-collection-duplicate",
                        "us_ach_debit_collected"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Duplicate collection entryReference");

        assertThatThrownBy(() -> service.create(
                        hkFpsRequestWithCurrency("USD"),
                        authorizationContext("collection-client-a", "corr-collection-hk-usd"),
                        "idem-collection-hk-usd",
                        "hk_fps_collection_completed"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("HK FPS Direct Debit collections must be denominated in HKD");
    }

    private CreateCollectionRequest usAchDebitRequest() {
        return usAchDebitRequestWithEntryReference("COLL-ENTRY-0001");
    }

    private CreateCollectionRequest usAchDebitRequestWithEntryReference(String firstEntryReference) {
        return new CreateCollectionRequest(
                "US_ACH_DIRECT_DEBIT_BATCH",
                "COLL-US-ACH-20260707-0001",
                "MANDATE-US-001",
                "CIB Collection Services",
                "Corporate Customer",
                settlementAccount(),
                null,
                null,
                null,
                null,
                List.of(
                        entry(firstEntryReference, "USD", "125.40"),
                        entry("COLL-ENTRY-0002", "USD", "225.10")));
    }

    private CreateCollectionRequest usAchDebitDuplicateEntryReferenceRequest() {
        return new CreateCollectionRequest(
                "US_ACH_DIRECT_DEBIT_BATCH",
                "COLL-US-ACH-20260707-0001",
                "MANDATE-US-001",
                "CIB Collection Services",
                "Corporate Customer",
                settlementAccount(),
                null,
                null,
                null,
                null,
                List.of(
                        entry("COLL-ENTRY-DUP", "USD", "125.40"),
                        entry("COLL-ENTRY-DUP", "USD", "225.10")));
    }

    private CreateCollectionRequest hkFpsRequest() {
        return hkFpsRequestWithCurrency("HKD");
    }

    private CreateCollectionRequest hkFpsRequestWithCurrency(String currency) {
        return new CreateCollectionRequest(
                "HK_FPS_DIRECT_DEBIT",
                "COLL-HK-FPS-20260707-0001",
                "EDDA-HK-001",
                "Sample Merchant HK",
                "Sample Payer HK",
                null,
                "004",
                "FPS-PROXY-ALIAS",
                new MoneyRequest(currency, "350.00"),
                "MONTHLY_TUTORIAL_FEE",
                List.of());
    }

    private AccountReferenceRequest settlementAccount() {
        return new AccountReferenceRequest("021000021", "123456789", "CIB Collections");
    }

    private CollectionEntryRequest entry(String entryReference, String currency, String value) {
        return new CollectionEntryRequest(
                entryReference,
                "Payer " + entryReference,
                new AccountReferenceRequest("111000025", "987654321", "Payer Account"),
                new MoneyRequest(currency, value),
                "INVOICE_COLLECTION");
    }

    private AuthorizationContext authorizationContext(String clientId, String correlationId) {
        return new AuthorizationContext(
                clientId,
                clientId,
                Set.of("collections:create", "collections:read"),
                "tenant-a",
                Map.of(),
                Instant.parse("2026-07-07T00:00:00Z"),
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
