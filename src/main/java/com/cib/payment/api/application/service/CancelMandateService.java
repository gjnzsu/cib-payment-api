package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.CancelMandateRequest;
import com.cib.payment.api.api.dto.MandateResponse;
import com.cib.payment.api.api.dto.PaymentLinksResponse;
import com.cib.payment.api.api.dto.PaymentReasonResponse;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.IdempotencyRepository;
import com.cib.payment.api.application.port.MandateRepository;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.domain.model.MandateId;
import com.cib.payment.api.domain.model.MandateRecord;
import com.cib.payment.api.domain.model.MandateStatus;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CancelMandateService {
    private final MandateRepository mandateRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final RequestFingerprintService fingerprintService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Object[] idempotencyLocks;

    @Autowired
    public CancelMandateService(
            MandateRepository mandateRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService) {
        this(mandateRepository, idempotencyRepository, fingerprintService, Clock.systemUTC());
    }

    CancelMandateService(
            MandateRepository mandateRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            Clock clock) {
        this.mandateRepository = mandateRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.fingerprintService = fingerprintService;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.clock = clock;
        this.idempotencyLocks = IntStream.range(0, 256).mapToObj(index -> new Object()).toArray();
    }

    public MandateResponse cancel(
            String mandateIdValue,
            CancelMandateRequest request,
            AuthorizationContext authorizationContext,
            String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationFailureException("Idempotency-Key is required");
        }
        var requestBody = request == null ? new CancelMandateRequest(null) : request;
        var mandateId = parseMandateId(mandateIdValue);
        var fingerprint = fingerprintService.fingerprint(
                authorizationContext.clientId(),
                "cancel-mandate",
                Map.of("mandateId", mandateId.value().toString(), "requestBody", requestBody));

        synchronized (idempotencyLock(authorizationContext.clientId(), idempotencyKey)) {
            var existing = idempotencyRepository.find(authorizationContext.clientId(), idempotencyKey);
            if (existing.isPresent()) {
                return replayOrConflict(existing.get(), fingerprint);
            }

            var record = mandateRepository.find(mandateId)
                    .filter(mandate -> mandate.clientId().equals(authorizationContext.clientId()))
                    .orElseThrow(() -> new PaymentNotFoundException("Mandate was not found"));
            if (record.status() != MandateStatus.ACTIVE && record.status() != MandateStatus.PENDING_AUTHORIZATION) {
                throw new ValidationFailureException("Only active or pending mandates can be cancelled");
            }

            var now = Instant.now(clock);
            var cancelled = record.withStatus(
                    MandateStatus.CANCELLED,
                    now,
                    new PaymentReason("MANDATE_CANCELLED", cancellationMessage(requestBody)));
            var response = toResponse(cancelled);
            var idempotencyRecord = new IdempotencyRecord(
                    authorizationContext.clientId(),
                    idempotencyKey,
                    fingerprint,
                    new PaymentId(cancelled.mandateId().value()),
                    PaymentStatus.REJECTED,
                    authorizationContext.correlationId(),
                    now,
                    now,
                    null,
                    writeResponseJson(response));
            var storedRecord = idempotencyRepository.saveIfAbsent(idempotencyRecord);
            if (!storedRecord.equals(idempotencyRecord)) {
                return replayOrConflict(storedRecord, fingerprint);
            }
            mandateRepository.save(cancelled);
            return response;
        }
    }

    private MandateId parseMandateId(String mandateIdValue) {
        try {
            return new MandateId(UUID.fromString(mandateIdValue));
        } catch (IllegalArgumentException exception) {
            throw new ValidationFailureException("mandateId must be a UUID string");
        }
    }

    private String cancellationMessage(CancelMandateRequest request) {
        if (request == null || request.cancellationReason() == null || request.cancellationReason().isBlank()) {
            return "Mandate was cancelled";
        }
        return "Mandate was cancelled: " + request.cancellationReason();
    }

    private MandateResponse replayOrConflict(IdempotencyRecord existing, String fingerprint) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException("Idempotency key was reused with a different mandate cancellation request");
        }
        try {
            return objectMapper.readValue(existing.originalResponseJson(), MandateResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to replay mandate cancellation response", exception);
        }
    }

    private String writeResponseJson(MandateResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store mandate cancellation response", exception);
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

    private Object idempotencyLock(String clientId, String idempotencyKey) {
        var hash = Math.floorMod((clientId + ":" + idempotencyKey).hashCode(), idempotencyLocks.length);
        return idempotencyLocks[hash];
    }
}
