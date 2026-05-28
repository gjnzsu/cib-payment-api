package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.CorrespondentSettlementContextResponse;
import com.cib.payment.api.api.dto.FiPaymentAcknowledgementResponse;
import com.cib.payment.api.api.dto.PaymentReasonResponse;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.FiCorrespondentPaymentSimulator;
import com.cib.payment.api.application.port.FiCorrespondentRouteProfilePort;
import com.cib.payment.api.application.port.FiPaymentRepository;
import com.cib.payment.api.application.port.IdempotencyRepository;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.CorrespondentSettlementContext;
import com.cib.payment.api.domain.model.FiPaymentCandidate;
import com.cib.payment.api.domain.model.FiPaymentId;
import com.cib.payment.api.domain.model.FiPaymentRecord;
import com.cib.payment.api.domain.model.FiPaymentStatus;
import com.cib.payment.api.domain.model.IdempotencyRecord;
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
import org.springframework.stereotype.Service;

@Service
public class CreateFiPaymentService {
    private final FiPaymentAdmissionService admissionService;
    private final FiCorrespondentRouteProfilePort routeProfile;
    private final FiCorrespondentPaymentSimulator simulator;
    private final FiPaymentRepository fiPaymentRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final RequestFingerprintService fingerprintService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Object[] idempotencyLocks;

    public CreateFiPaymentService(
            FiPaymentAdmissionService admissionService,
            FiCorrespondentRouteProfilePort routeProfile,
            FiCorrespondentPaymentSimulator simulator,
            FiPaymentRepository fiPaymentRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService) {
        this(admissionService, routeProfile, simulator, fiPaymentRepository, idempotencyRepository, fingerprintService, Clock.systemUTC());
    }

    CreateFiPaymentService(
            FiPaymentAdmissionService admissionService,
            FiCorrespondentRouteProfilePort routeProfile,
            FiCorrespondentPaymentSimulator simulator,
            FiPaymentRepository fiPaymentRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            Clock clock) {
        this.admissionService = admissionService;
        this.routeProfile = routeProfile;
        this.simulator = simulator;
        this.fiPaymentRepository = fiPaymentRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.fingerprintService = fingerprintService;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.clock = clock;
        this.idempotencyLocks = IntStream.range(0, 256).mapToObj(index -> new Object()).toArray();
    }

    public FiPaymentAcknowledgementResponse create(
            String rawXml,
            String contentType,
            AuthorizationContext authorizationContext,
            String idempotencyKey,
            String mockScenario) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationFailureException("Idempotency-Key is required");
        }

        var candidate = admissionService.admit(rawXml, contentType);
        var settlementContext = routeProfile.derive(
                candidate.instructingParty().bic(),
                candidate.instructedParty().bic(),
                candidate.settlementCurrency());
        var fingerprint = fingerprintService.fingerprint(
                authorizationContext.clientId(),
                candidate,
                Map.of("mockScenario", requireScenario(mockScenario)));

        synchronized (idempotencyLock(authorizationContext.clientId(), idempotencyKey)) {
            var existing = idempotencyRepository.find(authorizationContext.clientId(), idempotencyKey);
            if (existing.isPresent()) {
                return replayOrConflict(existing.get(), fingerprint);
            }

            var outcome = simulator.process(candidate, settlementContext, mockScenario);
            var now = Instant.now(clock);
            var paymentId = new FiPaymentId(UUID.randomUUID());
            var record = toRecord(
                    paymentId,
                    authorizationContext,
                    candidate,
                    settlementContext,
                    outcome.status(),
                    outcome.reason(),
                    now);
            var response = toAcknowledgement(record);
            var idempotencyRecord = new IdempotencyRecord(
                    authorizationContext.clientId(),
                    idempotencyKey,
                    fingerprint,
                    new PaymentId(paymentId.value()),
                    toPaymentStatus(outcome.status()),
                    authorizationContext.correlationId(),
                    now,
                    now,
                    null,
                    writeResponseJson(response));

            var storedRecord = idempotencyRepository.saveIfAbsent(idempotencyRecord);
            if (!storedRecord.equals(idempotencyRecord)) {
                return replayOrConflict(storedRecord, fingerprint);
            }

            fiPaymentRepository.save(record);
            return response;
        }
    }

    private String requireScenario(String mockScenario) {
        if (mockScenario == null || mockScenario.isBlank()) {
            throw new ValidationFailureException("FI payment simulator scenario is required");
        }
        return mockScenario;
    }

    private FiPaymentRecord toRecord(
            FiPaymentId paymentId,
            AuthorizationContext authorizationContext,
            FiPaymentCandidate candidate,
            CorrespondentSettlementContext settlementContext,
            FiPaymentStatus status,
            Optional<PaymentReason> reason,
            Instant now) {
        return new FiPaymentRecord(
                paymentId,
                authorizationContext.clientId(),
                candidate.identifiers(),
                candidate.instructingParty(),
                candidate.instructedParty(),
                candidate.settlementAmount(),
                candidate.settlementCurrency(),
                status,
                settlementContext,
                authorizationContext.correlationId(),
                now,
                now,
                reason);
    }

    private FiPaymentAcknowledgementResponse replayOrConflict(IdempotencyRecord existing, String fingerprint) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException("Idempotency key was reused with a different FI payment request");
        }
        if (existing.originalResponseJson() == null || existing.originalResponseJson().isBlank()) {
            throw new IdempotencyConflictException("Idempotency record does not contain an FI acknowledgement response");
        }
        try {
            return objectMapper.readValue(existing.originalResponseJson(), FiPaymentAcknowledgementResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to replay FI acknowledgement response", exception);
        }
    }

    private String writeResponseJson(FiPaymentAcknowledgementResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store FI acknowledgement response", exception);
        }
    }

    private FiPaymentAcknowledgementResponse toAcknowledgement(FiPaymentRecord record) {
        return new FiPaymentAcknowledgementResponse(
                record.paymentId().value().toString(),
                record.status().name(),
                toContextResponse(record.correspondentSettlementContext()),
                record.correlationId().value(),
                record.reason().map(this::toReasonResponse).orElse(null),
                "/v1/fi-payments/" + record.paymentId().value());
    }

    private CorrespondentSettlementContextResponse toContextResponse(CorrespondentSettlementContext context) {
        return new CorrespondentSettlementContextResponse(
                context.instructingAgent().bic(),
                context.instructedAgent().bic(),
                context.correspondentOrIntermediaryBank().map(party -> party.bic()).orElse(null),
                context.settlementCurrency(),
                context.accountRelationshipRole().name(),
                context.maskedSimulatedAccountReference());
    }

    private PaymentReasonResponse toReasonResponse(PaymentReason reason) {
        return new PaymentReasonResponse(reason.code(), reason.message());
    }

    private PaymentStatus toPaymentStatus(FiPaymentStatus status) {
        return switch (status) {
            case SETTLED -> PaymentStatus.COMPLETED;
            case PROCESSING -> PaymentStatus.PROCESSING;
            case REJECTED -> PaymentStatus.REJECTED;
        };
    }

    private Object idempotencyLock(String clientId, String idempotencyKey) {
        var hash = Math.floorMod((clientId + ":" + idempotencyKey).hashCode(), idempotencyLocks.length);
        return idempotencyLocks[hash];
    }
}
