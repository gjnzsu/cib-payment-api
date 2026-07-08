package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.api.dto.AccountReferenceRequest;
import com.cib.payment.api.api.dto.CollectionEntryRequest;
import com.cib.payment.api.api.dto.CreateCollectionRequest;
import com.cib.payment.api.api.dto.MoneyRequest;
import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.infrastructure.persistence.InMemoryCollectionRepository;
import com.cib.payment.api.infrastructure.persistence.InMemoryIdempotencyRepository;
import com.cib.payment.api.infrastructure.simulator.DeterministicCollectionSimulator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GetCollectionStatusServiceTest {
    private final InMemoryCollectionRepository collectionRepository = new InMemoryCollectionRepository();
    private final CreateCollectionService createService = new CreateCollectionService(
            collectionRepository,
            new InMemoryIdempotencyRepository(),
            new RequestFingerprintService(),
            new DeterministicCollectionSimulator());
    private final GetCollectionStatusService statusService = new GetCollectionStatusService(collectionRepository);

    @Test
    void ownerCanQueryCollectionStatus() {
        var createResponse = createService.create(
                usAchDebitRequest(),
                authorizationContext("collection-client-a", "corr-collection-created"),
                "idem-collection-status",
                "us_ach_debit_partially_returned");

        var status = statusService.getStatus(
                createResponse.collectionId(),
                authorizationContext("collection-client-a", "corr-collection-status"));

        assertThat(status.collectionId()).isEqualTo(createResponse.collectionId());
        assertThat(status.collectionProfile()).isEqualTo("US_ACH_DIRECT_DEBIT_BATCH");
        assertThat(status.collectionReference()).isEqualTo("COLL-US-ACH-20260707-0001");
        assertThat(status.mandateReference()).isEqualTo("MANDATE-US-001");
        assertThat(status.status()).isEqualTo("PARTIALLY_RETURNED");
        assertThat(status.entryCount()).isEqualTo(2);
        assertThat(status.entries().getFirst().status()).isEqualTo("RETURNED");
        assertThat(status.links().self()).isEqualTo("/v1/collections/" + createResponse.collectionId());
        assertThat(status.correlationId()).isEqualTo("corr-collection-created");
    }

    @Test
    void foreignOwnerAndUnknownCollectionReturnNotFound() {
        var createResponse = createService.create(
                usAchDebitRequest(),
                authorizationContext("collection-client-a", "corr-collection-created"),
                "idem-collection-foreign",
                "us_ach_debit_collected");

        assertThatThrownBy(() -> statusService.getStatus(
                        createResponse.collectionId(),
                        authorizationContext("collection-client-b", "corr-collection-foreign")))
                .isInstanceOf(PaymentNotFoundException.class);

        assertThatThrownBy(() -> statusService.getStatus(
                        "550e8400-e29b-41d4-a716-446655440000",
                        authorizationContext("collection-client-a", "corr-collection-unknown")))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void invalidCollectionIdFailsValidation() {
        assertThatThrownBy(() -> statusService.getStatus(
                        "not-a-uuid",
                        authorizationContext("collection-client-a", "corr-collection-invalid-id")))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("collectionId must be a UUID string");
    }

    private CreateCollectionRequest usAchDebitRequest() {
        return new CreateCollectionRequest(
                "US_ACH_DIRECT_DEBIT_BATCH",
                "COLL-US-ACH-20260707-0001",
                "MANDATE-US-001",
                "CIB Collection Services",
                "Corporate Customer",
                new AccountReferenceRequest("021000021", "123456789", "CIB Collections"),
                null,
                null,
                null,
                null,
                List.of(
                        entry("COLL-ENTRY-0001", "USD", "125.40"),
                        entry("COLL-ENTRY-0002", "USD", "225.10")));
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
}
