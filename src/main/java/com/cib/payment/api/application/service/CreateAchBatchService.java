package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.AccountReferenceRequest;
import com.cib.payment.api.api.dto.AchBatchEntryStatusResponse;
import com.cib.payment.api.api.dto.AchBatchResponse;
import com.cib.payment.api.api.dto.CreateAchBatchRequest;
import com.cib.payment.api.api.dto.MoneyRequest;
import com.cib.payment.api.api.dto.PaymentLinksResponse;
import com.cib.payment.api.api.dto.PaymentReasonResponse;
import com.cib.payment.api.application.exception.DetailedValidationFailureException;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.ValidationFailureDetail;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.AchBatchRepository;
import com.cib.payment.api.application.port.AchDirectCreditOutcome;
import com.cib.payment.api.application.port.AchDirectCreditSimulator;
import com.cib.payment.api.application.port.IdempotencyRepository;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.AchBatchEntry;
import com.cib.payment.api.domain.model.AchBatchId;
import com.cib.payment.api.domain.model.AchBatchRecord;
import com.cib.payment.api.domain.model.AchBatchStatus;
import com.cib.payment.api.domain.model.AchEntryId;
import com.cib.payment.api.domain.model.AchEntryStatus;
import com.cib.payment.api.domain.model.AuthorizationContext;
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
public class CreateAchBatchService {
    private static final String DEFAULT_MOCK_SCENARIO = "ach_direct_credit_accepted";
    private static final String RAIL = "ACH";
    private static final String USD = "USD";

    private final AchBatchRepository achBatchRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final RequestFingerprintService fingerprintService;
    private final AchDirectCreditSimulator simulator;
    private final PaymentObservability observability;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Object[] idempotencyLocks;

    @Autowired
    public CreateAchBatchService(
            AchBatchRepository achBatchRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            AchDirectCreditSimulator simulator,
            PaymentObservability observability) {
        this(achBatchRepository, idempotencyRepository, fingerprintService, simulator, observability, Clock.systemUTC());
    }

    CreateAchBatchService(
            AchBatchRepository achBatchRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            AchDirectCreditSimulator simulator) {
        this(
                achBatchRepository,
                idempotencyRepository,
                fingerprintService,
                simulator,
                PaymentObservability.noop(),
                Clock.systemUTC());
    }

    CreateAchBatchService(
            AchBatchRepository achBatchRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            AchDirectCreditSimulator simulator,
            Clock clock) {
        this(
                achBatchRepository,
                idempotencyRepository,
                fingerprintService,
                simulator,
                PaymentObservability.noop(),
                clock);
    }

    CreateAchBatchService(
            AchBatchRepository achBatchRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            AchDirectCreditSimulator simulator,
            PaymentObservability observability,
            Clock clock) {
        this.achBatchRepository = achBatchRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.fingerprintService = fingerprintService;
        this.simulator = simulator;
        this.observability = observability;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.clock = clock;
        this.idempotencyLocks = IntStream.range(0, 256).mapToObj(index -> new Object()).toArray();
    }

    public AchBatchResponse create(
            CreateAchBatchRequest request,
            AuthorizationContext authorizationContext,
            String idempotencyKey,
            String mockScenario) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationFailureException("Idempotency-Key is required");
        }
        validateAchRules(request);

        var scenario = scenarioOrDefault(mockScenario);
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
            var acceptedRecord = acceptedRecord(request, authorizationContext, now);
            var outcome = simulator.process(acceptedRecord, authorizationContext, scenario);
            var completedRecord = applyOutcome(acceptedRecord, outcome, now);
            var response = toResponse(completedRecord);
            var idempotencyRecord = new IdempotencyRecord(
                    authorizationContext.clientId(),
                    idempotencyKey,
                    fingerprint,
                    new PaymentId(completedRecord.batchId().value()),
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

            achBatchRepository.save(completedRecord);
            observability.achBatchAccepted(completedRecord, authorizationContext);
            return response;
        }
    }

    private void validateAchRules(CreateAchBatchRequest request) {
        var references = new HashSet<String>();
        for (var index = 0; index < request.entries().size(); index++) {
            var entry = request.entries().get(index);
            if (!references.add(entry.entryReference())) {
                throw new DetailedValidationFailureException(
                        "Duplicate ACH entryReference: " + entry.entryReference(),
                        List.of(new ValidationFailureDetail(
                                "entries[" + index + "].entryReference",
                                "Duplicate ACH entryReference")));
            }
            if (!USD.equals(entry.amount().currency())) {
                throw new ValidationFailureException("ACH direct credit entries must be denominated in USD");
            }
        }
    }

    private AchBatchRecord acceptedRecord(
            CreateAchBatchRequest request,
            AuthorizationContext authorizationContext,
            Instant now) {
        return new AchBatchRecord(
                new AchBatchId(UUID.randomUUID()),
                authorizationContext.clientId(),
                request.batchReference(),
                request.originatorName(),
                request.effectiveEntryDate(),
                toAccount(request.settlementAccount()),
                request.entries().stream().map(this::toEntry).toList(),
                AchBatchStatus.ACCEPTED_FOR_CLEARING,
                authorizationContext.correlationId(),
                now,
                now,
                Optional.empty());
    }

    private AchBatchEntry toEntry(com.cib.payment.api.api.dto.AchBatchEntryRequest entry) {
        return new AchBatchEntry(
                new AchEntryId(UUID.randomUUID()),
                entry.entryReference(),
                entry.receiverName(),
                toAccount(entry.receiverAccount()),
                toMoney(entry.amount()),
                new PaymentReason("ACH_PURPOSE", entry.purpose()),
                AchEntryStatus.ACCEPTED,
                Optional.empty());
    }

    private AchBatchRecord applyOutcome(
            AchBatchRecord acceptedRecord,
            AchDirectCreditOutcome outcome,
            Instant now) {
        var outcomesByEntryId = outcome.entryOutcomes().stream()
                .collect(Collectors.toMap(AchDirectCreditOutcome.EntryOutcome::entryId, entry -> entry));
        var entries = acceptedRecord.entries().stream()
                .map(entry -> {
                    var entryOutcome = outcomesByEntryId.get(entry.entryId());
                    if (entryOutcome == null) {
                        return entry;
                    }
                    return new AchBatchEntry(
                            entry.entryId(),
                            entry.entryReference(),
                            entry.receiverName(),
                            entry.receiverAccount(),
                            entry.amount(),
                            entry.purpose(),
                            entryOutcome.status(),
                            entryOutcome.reason());
                })
                .toList();

        return new AchBatchRecord(
                acceptedRecord.batchId(),
                acceptedRecord.clientId(),
                acceptedRecord.batchReference(),
                acceptedRecord.originatorName(),
                acceptedRecord.effectiveEntryDate(),
                acceptedRecord.settlementAccount(),
                entries,
                outcome.batchStatus(),
                acceptedRecord.correlationId(),
                acceptedRecord.createdAt(),
                now,
                outcome.reason());
    }

    private AchBatchResponse replayOrConflict(IdempotencyRecord existing, String fingerprint) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException("Idempotency key was reused with a different ACH batch request");
        }
        if (existing.originalResponseJson() == null || existing.originalResponseJson().isBlank()) {
            throw new IdempotencyConflictException("Idempotency record does not contain an ACH batch response");
        }
        try {
            return objectMapper.readValue(existing.originalResponseJson(), AchBatchResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to replay ACH batch response", exception);
        }
    }

    private String writeResponseJson(AchBatchResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store ACH batch response", exception);
        }
    }

    private AchBatchResponse toResponse(AchBatchRecord record) {
        var batchId = record.batchId().value().toString();
        return new AchBatchResponse(
                batchId,
                RAIL,
                record.status().name(),
                record.entryCount(),
                toMoneyRequest(record.totalAmount()),
                record.entries().stream().map(this::toEntryStatus).toList(),
                record.createdAt(),
                record.updatedAt(),
                record.correlationId().value(),
                record.reason().map(this::toReasonResponse).orElse(null),
                new PaymentLinksResponse(null, "/v1/ach-batches/" + batchId));
    }

    private AchBatchEntryStatusResponse toEntryStatus(AchBatchEntry entry) {
        return new AchBatchEntryStatusResponse(
                entry.entryId().value().toString(),
                entry.entryReference(),
                entry.receiverName(),
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

    private PaymentStatus toPaymentStatus(AchBatchStatus status) {
        return switch (status) {
            case SETTLED -> PaymentStatus.COMPLETED;
            case ACCEPTED_FOR_CLEARING, SETTLEMENT_PENDING -> PaymentStatus.PROCESSING;
            case PARTIALLY_RETURNED, REJECTED -> PaymentStatus.REJECTED;
        };
    }

    private String scenarioOrDefault(String mockScenario) {
        if (mockScenario == null || mockScenario.isBlank()) {
            return DEFAULT_MOCK_SCENARIO;
        }
        return mockScenario.trim();
    }

    private Object idempotencyLock(String clientId, String idempotencyKey) {
        var hash = Math.floorMod((clientId + ":" + idempotencyKey).hashCode(), idempotencyLocks.length);
        return idempotencyLocks[hash];
    }
}
