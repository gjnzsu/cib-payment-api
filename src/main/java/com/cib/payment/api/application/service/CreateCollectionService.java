package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.AccountReferenceRequest;
import com.cib.payment.api.api.dto.CollectionEntryRequest;
import com.cib.payment.api.api.dto.CollectionEntryStatusResponse;
import com.cib.payment.api.api.dto.CollectionResponse;
import com.cib.payment.api.api.dto.CreateCollectionRequest;
import com.cib.payment.api.api.dto.MoneyRequest;
import com.cib.payment.api.api.dto.PaymentLinksResponse;
import com.cib.payment.api.api.dto.PaymentReasonResponse;
import com.cib.payment.api.application.exception.DetailedValidationFailureException;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.ValidationFailureDetail;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.CollectionRepository;
import com.cib.payment.api.application.port.CollectionSimulator;
import com.cib.payment.api.application.port.CollectionSimulatorOutcome;
import com.cib.payment.api.application.port.IdempotencyRepository;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CollectionEntry;
import com.cib.payment.api.domain.model.CollectionEntryId;
import com.cib.payment.api.domain.model.CollectionEntryStatus;
import com.cib.payment.api.domain.model.CollectionId;
import com.cib.payment.api.domain.model.CollectionProfile;
import com.cib.payment.api.domain.model.CollectionRecord;
import com.cib.payment.api.domain.model.CollectionStatus;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CreateCollectionService {
    private static final String DEFAULT_US_ACH_SCENARIO = "us_ach_debit_collected";
    private static final String DEFAULT_HK_FPS_SCENARIO = "hk_fps_collection_completed";
    private static final String USD = "USD";
    private static final String HKD = "HKD";

    private final CollectionRepository collectionRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final RequestFingerprintService fingerprintService;
    private final CollectionSimulator simulator;
    private final PaymentObservability observability;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Object[] idempotencyLocks;

    @Autowired
    public CreateCollectionService(
            CollectionRepository collectionRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            CollectionSimulator simulator,
            PaymentObservability observability) {
        this(collectionRepository, idempotencyRepository, fingerprintService, simulator, observability, Clock.systemUTC());
    }

    CreateCollectionService(
            CollectionRepository collectionRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            CollectionSimulator simulator) {
        this(collectionRepository, idempotencyRepository, fingerprintService, simulator, PaymentObservability.noop(), Clock.systemUTC());
    }

    CreateCollectionService(
            CollectionRepository collectionRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            CollectionSimulator simulator,
            Clock clock) {
        this(collectionRepository, idempotencyRepository, fingerprintService, simulator, PaymentObservability.noop(), clock);
    }

    CreateCollectionService(
            CollectionRepository collectionRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            CollectionSimulator simulator,
            PaymentObservability observability,
            Clock clock) {
        this.collectionRepository = collectionRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.fingerprintService = fingerprintService;
        this.simulator = simulator;
        this.observability = observability;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.clock = clock;
        this.idempotencyLocks = IntStream.range(0, 256).mapToObj(index -> new Object()).toArray();
    }

    public CollectionResponse create(
            CreateCollectionRequest request,
            AuthorizationContext authorizationContext,
            String idempotencyKey,
            String mockScenario) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationFailureException("Idempotency-Key is required");
        }

        var profile = parseProfile(request.collectionProfile());
        validateCollectionRules(request, profile);
        var scenario = scenarioOrDefault(mockScenario, profile);
        var fingerprint = fingerprintService.fingerprint(
                authorizationContext.clientId(),
                request,
                Map.of("mockScenario", scenario));

        synchronized (idempotencyLock(authorizationContext.clientId(), idempotencyKey)) {
            var existing = idempotencyRepository.find(authorizationContext.clientId(), idempotencyKey);
            if (existing.isPresent()) {
                return replayOrConflict(existing.get(), fingerprint);
            }

            var now = Instant.now(clock);
            var acceptedRecord = acceptedRecord(request, profile, authorizationContext, now);
            var outcome = simulator.process(acceptedRecord, authorizationContext, scenario);
            var completedRecord = applyOutcome(acceptedRecord, outcome, now);
            var response = toResponse(completedRecord);
            var idempotencyRecord = new IdempotencyRecord(
                    authorizationContext.clientId(),
                    idempotencyKey,
                    fingerprint,
                    new PaymentId(completedRecord.collectionId().value()),
                    toPaymentStatus(completedRecord.status()),
                    authorizationContext.correlationId(),
                    now,
                    now,
                    null,
                    writeResponseJson(response));

            var storedRecord = idempotencyRepository.saveIfAbsent(idempotencyRecord);
            if (!storedRecord.equals(idempotencyRecord)) {
                return replayOrConflict(storedRecord, fingerprint);
            }

            collectionRepository.save(completedRecord);
            observability.collectionAccepted(completedRecord, authorizationContext);
            return response;
        }
    }

    private CollectionProfile parseProfile(String value) {
        try {
            return CollectionProfile.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ValidationFailureException("Unsupported collectionProfile: " + value);
        }
    }

    private void validateCollectionRules(CreateCollectionRequest request, CollectionProfile profile) {
        if (request.mandateReference() == null || request.mandateReference().isBlank()) {
            throw new ValidationFailureException("mandateReference is required");
        }
        switch (profile) {
            case US_ACH_DIRECT_DEBIT_BATCH -> validateUsAchDebit(request);
            case HK_FPS_DIRECT_DEBIT -> validateHkFps(request);
        }
    }

    private void validateUsAchDebit(CreateCollectionRequest request) {
        if (request.settlementAccount() == null) {
            throw new ValidationFailureException("settlementAccount is required for US ACH Direct Debit collections");
        }
        if (request.entries().isEmpty()) {
            throw new ValidationFailureException("US ACH Direct Debit collections require at least one entry");
        }
        var references = new HashSet<String>();
        for (var index = 0; index < request.entries().size(); index++) {
            var entry = request.entries().get(index);
            if (!references.add(entry.entryReference())) {
                throw new DetailedValidationFailureException(
                        "Duplicate collection entryReference: " + entry.entryReference(),
                        List.of(new ValidationFailureDetail(
                                "entries[" + index + "].entryReference",
                                "Duplicate collection entryReference")));
            }
            if (!USD.equals(entry.amount().currency())) {
                throw new ValidationFailureException("US ACH Direct Debit collection entries must be denominated in USD");
            }
        }
    }

    private void validateHkFps(CreateCollectionRequest request) {
        if (request.amount() == null) {
            throw new ValidationFailureException("amount is required for HK FPS Direct Debit collections");
        }
        if (!HKD.equals(request.amount().currency())) {
            throw new ValidationFailureException("HK FPS Direct Debit collections must be denominated in HKD");
        }
    }

    private CollectionRecord acceptedRecord(
            CreateCollectionRequest request,
            CollectionProfile profile,
            AuthorizationContext authorizationContext,
            Instant now) {
        return new CollectionRecord(
                new CollectionId(UUID.randomUUID()),
                authorizationContext.clientId(),
                profile,
                request.collectionReference(),
                request.mandateReference(),
                request.creditorName(),
                request.debtorName(),
                request.settlementAccount() == null ? null : toAccount(request.settlementAccount()),
                request.payerBankCode(),
                request.payerAlias(),
                request.amount() == null ? null : toMoney(request.amount()),
                request.purpose() == null ? null : new PaymentReason("COLLECTION_PURPOSE", request.purpose()),
                request.entries().stream().map(this::toEntry).toList(),
                CollectionStatus.ACCEPTED,
                authorizationContext.correlationId(),
                now,
                now,
                Optional.empty());
    }

    private CollectionEntry toEntry(CollectionEntryRequest entry) {
        return new CollectionEntry(
                new CollectionEntryId(UUID.randomUUID()),
                entry.entryReference(),
                entry.payerName(),
                toAccount(entry.payerAccount()),
                toMoney(entry.amount()),
                new PaymentReason("COLLECTION_PURPOSE", entry.purpose()),
                CollectionEntryStatus.ACCEPTED,
                Optional.empty());
    }

    private CollectionRecord applyOutcome(
            CollectionRecord acceptedRecord,
            CollectionSimulatorOutcome outcome,
            Instant now) {
        var outcomesByEntryId = outcome.entryOutcomes().stream()
                .collect(Collectors.toMap(CollectionSimulatorOutcome.EntryOutcome::entryId, entry -> entry));
        var entries = acceptedRecord.entries().stream()
                .map(entry -> {
                    var entryOutcome = outcomesByEntryId.get(entry.entryId());
                    if (entryOutcome == null) {
                        return entry;
                    }
                    return new CollectionEntry(
                            entry.entryId(),
                            entry.entryReference(),
                            entry.payerName(),
                            entry.payerAccount(),
                            entry.amount(),
                            entry.purpose(),
                            entryOutcome.status(),
                            entryOutcome.reason());
                })
                .toList();

        return new CollectionRecord(
                acceptedRecord.collectionId(),
                acceptedRecord.clientId(),
                acceptedRecord.collectionProfile(),
                acceptedRecord.collectionReference(),
                acceptedRecord.mandateReference(),
                acceptedRecord.creditorName(),
                acceptedRecord.debtorName(),
                acceptedRecord.settlementAccount(),
                acceptedRecord.payerBankCode(),
                acceptedRecord.payerAlias(),
                acceptedRecord.amount(),
                acceptedRecord.purpose(),
                entries,
                outcome.status(),
                acceptedRecord.correlationId(),
                acceptedRecord.createdAt(),
                now,
                outcome.reason());
    }

    private CollectionResponse replayOrConflict(IdempotencyRecord existing, String fingerprint) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException("Idempotency key was reused with a different collection request");
        }
        if (existing.originalResponseJson() == null || existing.originalResponseJson().isBlank()) {
            throw new IdempotencyConflictException("Idempotency record does not contain a collection response");
        }
        try {
            return objectMapper.readValue(existing.originalResponseJson(), CollectionResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to replay collection response", exception);
        }
    }

    private String writeResponseJson(CollectionResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store collection response", exception);
        }
    }

    private CollectionResponse toResponse(CollectionRecord record) {
        var collectionId = record.collectionId().value().toString();
        return new CollectionResponse(
                collectionId,
                record.collectionProfile().name(),
                record.status().name(),
                record.entryCount(),
                toMoneyRequest(record.totalAmount()),
                record.entries().stream().map(this::toEntryStatus).toList(),
                record.createdAt(),
                record.updatedAt(),
                record.correlationId().value(),
                record.reason().map(this::toReasonResponse).orElse(null),
                new PaymentLinksResponse(null, "/v1/collections/" + collectionId));
    }

    private CollectionEntryStatusResponse toEntryStatus(CollectionEntry entry) {
        return new CollectionEntryStatusResponse(
                entry.entryId().value().toString(),
                entry.entryReference(),
                entry.payerName(),
                toMoneyRequest(entry.amount()),
                entry.status().name(),
                entry.reason().map(this::toReasonResponse).orElse(null));
    }

    private AccountReference toAccount(AccountReferenceRequest account) {
        return new AccountReference(account.bankCode(), account.accountNumber(), account.accountName());
    }

    private Money toMoney(MoneyRequest money) {
        return new Money(money.currency(), money.value());
    }

    private MoneyRequest toMoneyRequest(Money money) {
        return new MoneyRequest(money.currency(), money.value());
    }

    private PaymentReasonResponse toReasonResponse(PaymentReason reason) {
        return new PaymentReasonResponse(reason.code(), reason.message());
    }

    private PaymentStatus toPaymentStatus(CollectionStatus status) {
        return switch (status) {
            case COLLECTED, COMPLETED -> PaymentStatus.COMPLETED;
            case ACCEPTED, SETTLEMENT_PENDING, PENDING_AUTHORIZATION -> PaymentStatus.PROCESSING;
            case PARTIALLY_RETURNED, REJECTED -> PaymentStatus.REJECTED;
            case TIMEOUT -> PaymentStatus.TIMEOUT;
            case FAILED -> PaymentStatus.FAILED;
        };
    }

    private String scenarioOrDefault(String mockScenario, CollectionProfile profile) {
        if (mockScenario != null && !mockScenario.isBlank()) {
            return mockScenario.trim();
        }
        return switch (profile) {
            case US_ACH_DIRECT_DEBIT_BATCH -> DEFAULT_US_ACH_SCENARIO;
            case HK_FPS_DIRECT_DEBIT -> DEFAULT_HK_FPS_SCENARIO;
        };
    }

    private Object idempotencyLock(String clientId, String idempotencyKey) {
        var hash = Math.floorMod((clientId + ":" + idempotencyKey).hashCode(), idempotencyLocks.length);
        return idempotencyLocks[hash];
    }
}
