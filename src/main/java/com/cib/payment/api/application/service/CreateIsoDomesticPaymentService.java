package com.cib.payment.api.application.service;

import com.cib.payment.api.application.exception.DownstreamProcessingException;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.IdempotencyRepository;
import com.cib.payment.api.application.port.PaymentEngineInitiationPort;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

@Service
public class CreateIsoDomesticPaymentService {
    private final IsoPaymentAdmissionService admissionService;
    private final RequestFingerprintService fingerprintService;
    private final IdempotencyRepository idempotencyRepository;
    private final PaymentEngineInitiationPort paymentEngine;
    private final PaymentObservability observability;
    private final Object[] idempotencyLocks;

    public CreateIsoDomesticPaymentService(
            IsoPaymentAdmissionService admissionService,
            RequestFingerprintService fingerprintService,
            IdempotencyRepository idempotencyRepository,
            PaymentEngineInitiationPort paymentEngine,
            PaymentObservability observability) {
        this.admissionService = admissionService;
        this.fingerprintService = fingerprintService;
        this.idempotencyRepository = idempotencyRepository;
        this.paymentEngine = paymentEngine;
        this.observability = observability;
        this.idempotencyLocks = IntStream.range(0, 256)
                .mapToObj(index -> new Object())
                .toArray();
    }

    public String create(
            String rawXml,
            String contentType,
            AuthorizationContext authorizationContext,
            String idempotencyKey,
            String mockScenario) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationFailureException("Idempotency-Key is required");
        }
        if (rawXml == null || rawXml.isBlank()) {
            throw new ValidationFailureException("pain.001 XML request body is required");
        }

        var candidate = admissionService.admit(rawXml, contentType);
        var normalizedScenario = normalizeScenario(mockScenario);
        var fingerprint = fingerprintService.fingerprint(
                authorizationContext.clientId(),
                candidate,
                Map.of("mockScenario", normalizedScenario));

        synchronized (idempotencyLock(authorizationContext.clientId(), idempotencyKey)) {
            var existing = idempotencyRepository.find(authorizationContext.clientId(), idempotencyKey);
            if (existing.isPresent()) {
                return replayOrConflict(existing.get(), fingerprint, authorizationContext.correlationId());
            }

            var engineRecord = paymentEngine.initiate(
                    candidate,
                    authorizationContext,
                    authorizationContext.correlationId(),
                    idempotencyKey,
                    normalizedScenario);
            var responseXml = engineRecord.latestStatusReportXml()
                    .orElseThrow(() -> new DownstreamProcessingException("Payment Engine did not produce pain.002 status report"));
            var idempotencyRecord = new IdempotencyRecord(
                    authorizationContext.clientId(),
                    idempotencyKey,
                    fingerprint,
                    engineRecord.paymentId(),
                    engineRecord.status(),
                    authorizationContext.correlationId(),
                    engineRecord.createdAt(),
                    engineRecord.updatedAt(),
                    responseXml);

            var storedRecord = idempotencyRepository.saveIfAbsent(idempotencyRecord);
            if (!storedRecord.equals(idempotencyRecord)) {
                return replayOrConflict(storedRecord, fingerprint, authorizationContext.correlationId());
            }
            return responseXml;
        }
    }

    private String replayOrConflict(
            IdempotencyRecord existing,
            String fingerprint,
            CorrelationId correlationId) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            observability.idempotencyConflict(existing, correlationId);
            throw new IdempotencyConflictException("Idempotency key was reused with a different request body");
        }
        observability.idempotencyReplay(existing);
        if (existing.originalResponseXml() == null || existing.originalResponseXml().isBlank()) {
            throw new IdempotencyConflictException("Idempotency record does not contain an ISO response link");
        }
        return existing.originalResponseXml();
    }

    private String normalizeScenario(String mockScenario) {
        return mockScenario == null || mockScenario.isBlank() ? "success" : mockScenario;
    }

    private Object idempotencyLock(String clientId, String idempotencyKey) {
        var hash = Math.floorMod((clientId + ":" + idempotencyKey).hashCode(), idempotencyLocks.length);
        return idempotencyLocks[hash];
    }
}
