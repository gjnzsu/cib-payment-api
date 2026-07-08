package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.AccountReferenceRequest;
import com.cib.payment.api.api.dto.CreateMandateRequest;
import com.cib.payment.api.api.dto.MandateResponse;
import com.cib.payment.api.api.dto.PaymentLinksResponse;
import com.cib.payment.api.api.dto.PaymentReasonResponse;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.IdempotencyRepository;
import com.cib.payment.api.application.port.MandateRepository;
import com.cib.payment.api.application.port.MandateSimulator;
import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.domain.model.MandateId;
import com.cib.payment.api.domain.model.MandateProfile;
import com.cib.payment.api.domain.model.MandateRecord;
import com.cib.payment.api.domain.model.MandateStatus;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CreateMandateService {
    private static final String DEFAULT_SCENARIO = "mandate_active";
    private static final String USD = "USD";
    private static final String HKD = "HKD";

    private final MandateRepository mandateRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final RequestFingerprintService fingerprintService;
    private final MandateSimulator simulator;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Object[] idempotencyLocks;

    @Autowired
    public CreateMandateService(
            MandateRepository mandateRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            MandateSimulator simulator) {
        this(mandateRepository, idempotencyRepository, fingerprintService, simulator, Clock.systemUTC());
    }

    CreateMandateService(
            MandateRepository mandateRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            MandateSimulator simulator,
            Clock clock) {
        this.mandateRepository = mandateRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.fingerprintService = fingerprintService;
        this.simulator = simulator;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.clock = clock;
        this.idempotencyLocks = IntStream.range(0, 256).mapToObj(index -> new Object()).toArray();
    }

    public MandateResponse create(
            CreateMandateRequest request,
            AuthorizationContext authorizationContext,
            String idempotencyKey,
            String mockScenario) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationFailureException("Idempotency-Key is required");
        }

        var profile = parseProfile(request.mandateProfile());
        validateMandateRules(request, profile);
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
            var acceptedRecord = acceptedRecord(request, profile, authorizationContext, now);
            ensureUniqueMandateReference(acceptedRecord);
            var outcome = simulator.process(acceptedRecord, authorizationContext, scenario);
            var completedRecord = acceptedRecord.withStatus(
                    outcome.status(),
                    now,
                    outcome.reason().orElse(null));
            var response = toResponse(completedRecord);
            var idempotencyRecord = new IdempotencyRecord(
                    authorizationContext.clientId(),
                    idempotencyKey,
                    fingerprint,
                    new PaymentId(completedRecord.mandateId().value()),
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

            mandateRepository.save(completedRecord);
            return response;
        }
    }

    private void ensureUniqueMandateReference(MandateRecord acceptedRecord) {
        mandateRepository
                .findByClientIdAndMandateReference(acceptedRecord.clientId(), acceptedRecord.mandateReference())
                .ifPresent(existing -> {
                    throw new ValidationFailureException("mandateReference already exists for authenticated client");
                });
    }

    private MandateProfile parseProfile(String value) {
        try {
            return MandateProfile.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ValidationFailureException("Unsupported mandateProfile: " + value);
        }
    }

    private void validateMandateRules(CreateMandateRequest request, MandateProfile profile) {
        switch (profile) {
            case US_ACH_DEBIT_MANDATE -> {
                if (!USD.equals(request.maximumAmount().currency())) {
                    throw new ValidationFailureException("US ACH debit mandates must be denominated in USD");
                }
            }
            case HK_FPS_EDDA -> {
                if (!HKD.equals(request.maximumAmount().currency())) {
                    throw new ValidationFailureException("HK FPS eDDA mandates must be denominated in HKD");
                }
            }
        }
    }

    private MandateRecord acceptedRecord(
            CreateMandateRequest request,
            MandateProfile profile,
            AuthorizationContext authorizationContext,
            Instant now) {
        var mandateId = UUID.randomUUID();
        var mandateReference = request.mandateReference() == null || request.mandateReference().isBlank()
                ? "MND-" + mandateId.toString().substring(0, 8).toUpperCase()
                : request.mandateReference();
        return new MandateRecord(
                new MandateId(mandateId),
                authorizationContext.clientId(),
                profile,
                mandateReference,
                request.creditorName(),
                request.debtorName(),
                toAccount(request.creditorAccount()),
                toAccount(request.debtorAccount()),
                new Money(request.maximumAmount().currency(), request.maximumAmount().value()),
                request.frequency(),
                new PaymentReason("MANDATE_PURPOSE", request.purpose()),
                MandateStatus.PENDING_AUTHORIZATION,
                authorizationContext.correlationId(),
                now,
                now,
                Optional.empty());
    }

    private AccountReference toAccount(AccountReferenceRequest account) {
        return new AccountReference(account.bankCode(), account.accountNumber(), account.accountName());
    }

    private MandateResponse replayOrConflict(IdempotencyRecord existing, String fingerprint) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException("Idempotency key was reused with a different mandate request");
        }
        if (existing.originalResponseJson() == null || existing.originalResponseJson().isBlank()) {
            throw new IdempotencyConflictException("Idempotency record does not contain a mandate response");
        }
        try {
            return objectMapper.readValue(existing.originalResponseJson(), MandateResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to replay mandate response", exception);
        }
    }

    private String writeResponseJson(MandateResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store mandate response", exception);
        }
    }

    private MandateResponse toResponse(MandateRecord record) {
        var mandateId = record.mandateId().value().toString();
        return new MandateResponse(
                mandateId,
                record.mandateReference(),
                record.mandateProfile().name(),
                record.status().name(),
                record.createdAt(),
                record.updatedAt(),
                record.correlationId().value(),
                record.reason().map(this::toReasonResponse).orElse(null),
                new PaymentLinksResponse(null, "/v1/mandates/" + mandateId));
    }

    private PaymentReasonResponse toReasonResponse(PaymentReason reason) {
        return new PaymentReasonResponse(reason.code(), reason.message());
    }

    private PaymentStatus toPaymentStatus(MandateStatus status) {
        return switch (status) {
            case ACTIVE -> PaymentStatus.COMPLETED;
            case PENDING_AUTHORIZATION -> PaymentStatus.PROCESSING;
            case REJECTED, CANCELLED, EXPIRED -> PaymentStatus.REJECTED;
            case TIMEOUT -> PaymentStatus.TIMEOUT;
            case FAILED -> PaymentStatus.FAILED;
        };
    }

    private String scenarioOrDefault(String mockScenario) {
        return mockScenario == null || mockScenario.isBlank() ? DEFAULT_SCENARIO : mockScenario.trim();
    }

    private Object idempotencyLock(String clientId, String idempotencyKey) {
        var hash = Math.floorMod((clientId + ":" + idempotencyKey).hashCode(), idempotencyLocks.length);
        return idempotencyLocks[hash];
    }
}
