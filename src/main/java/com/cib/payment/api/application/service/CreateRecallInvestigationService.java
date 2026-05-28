package com.cib.payment.api.application.service;

import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.PaymentNotFoundException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.Camt056RecallRequestParser;
import com.cib.payment.api.application.port.FiPaymentRepository;
import com.cib.payment.api.application.port.IdempotencyRepository;
import com.cib.payment.api.application.port.RecallInvestigationOutcome;
import com.cib.payment.api.application.port.RecallInvestigationRepository;
import com.cib.payment.api.application.port.RecallInvestigationSimulator;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.FiPaymentId;
import com.cib.payment.api.domain.model.FiPaymentRecord;
import com.cib.payment.api.domain.model.FiPaymentStatus;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentStatus;
import com.cib.payment.api.domain.model.RecallInvestigationId;
import com.cib.payment.api.domain.model.RecallInvestigationRecord;
import com.cib.payment.api.domain.model.RecallInvestigationStatus;
import com.cib.payment.api.infrastructure.iso.Camt029Renderer;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;

@Service
public class CreateRecallInvestigationService {
    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "application/camt.056+xml",
            "application/xml",
            "text/xml");
    private static final Set<String> SUPPORTED_RECALL_REASONS = Set.of("DUPL", "CUST", "AM09", "FRAD", "TECH");

    private final Camt056RecallRequestParser parser;
    private final RecallInvestigationSimulator simulator;
    private final Camt029Renderer renderer;
    private final FiPaymentRepository fiPaymentRepository;
    private final RecallInvestigationRepository recallInvestigationRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final RequestFingerprintService fingerprintService;
    private final Clock clock;
    private final Object[] idempotencyLocks;

    public CreateRecallInvestigationService(
            Camt056RecallRequestParser parser,
            RecallInvestigationSimulator simulator,
            Camt029Renderer renderer,
            FiPaymentRepository fiPaymentRepository,
            RecallInvestigationRepository recallInvestigationRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService) {
        this(
                parser,
                simulator,
                renderer,
                fiPaymentRepository,
                recallInvestigationRepository,
                idempotencyRepository,
                fingerprintService,
                Clock.systemUTC());
    }

    CreateRecallInvestigationService(
            Camt056RecallRequestParser parser,
            RecallInvestigationSimulator simulator,
            Camt029Renderer renderer,
            FiPaymentRepository fiPaymentRepository,
            RecallInvestigationRepository recallInvestigationRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            Clock clock) {
        this.parser = parser;
        this.simulator = simulator;
        this.renderer = renderer;
        this.fiPaymentRepository = fiPaymentRepository;
        this.recallInvestigationRepository = recallInvestigationRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.fingerprintService = fingerprintService;
        this.clock = clock;
        this.idempotencyLocks = IntStream.range(0, 256).mapToObj(index -> new Object()).toArray();
    }

    public String create(
            String fiPaymentIdValue,
            String rawXml,
            String contentType,
            AuthorizationContext authorizationContext,
            String idempotencyKey,
            String mockScenario) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationFailureException("Idempotency-Key is required");
        }
        validateBodyAndContentType(rawXml, contentType);

        var paymentId = parsePaymentId(fiPaymentIdValue);
        var payment = fiPaymentRepository.findByIdAndOwnerClientId(paymentId, authorizationContext.clientId())
                .orElseThrow(() -> new PaymentNotFoundException("Payment was not found"));
        validateRecallEligibility(payment);

        var recallRequest = parser.parse(rawXml);
        validateRecallRequest(recallRequest, payment);

        var scenario = requireScenario(mockScenario);
        var fingerprint = fingerprintService.fingerprint(
                authorizationContext.clientId(),
                paymentId,
                recallRequest,
                Map.of("mockScenario", scenario));

        synchronized (idempotencyLock(authorizationContext.clientId(), idempotencyKey)) {
            var existing = idempotencyRepository.find(authorizationContext.clientId(), idempotencyKey);
            if (existing.isPresent()) {
                return replayOrConflict(existing.get(), fingerprint);
            }

            if (recallInvestigationRepository.findByPaymentId(paymentId).isPresent()) {
                throw new IdempotencyConflictException("Recall investigation already exists for FI payment");
            }

            var outcome = simulator.investigate(scenario);
            var now = Instant.now(clock);
            var record = toRecord(payment, recallRequest, outcome, authorizationContext, now);
            var responseXml = renderer.render(record);

            var storedRecall = recallInvestigationRepository.saveIfAbsent(record);
            if (!storedRecall.equals(record)) {
                throw new IdempotencyConflictException("Recall investigation already exists for FI payment");
            }

            var idempotencyRecord = new IdempotencyRecord(
                    authorizationContext.clientId(),
                    idempotencyKey,
                    fingerprint,
                    new PaymentId(paymentId.value()),
                    toPaymentStatus(outcome.status()),
                    authorizationContext.correlationId(),
                    now,
                    now,
                    responseXml,
                    null);

            var storedIdempotencyRecord = idempotencyRepository.saveIfAbsent(idempotencyRecord);
            if (!storedIdempotencyRecord.equals(idempotencyRecord)) {
                return replayOrConflict(storedIdempotencyRecord, fingerprint);
            }

            return responseXml;
        }
    }

    private void validateBodyAndContentType(String rawXml, String contentType) {
        if (rawXml == null || rawXml.isBlank()) {
            throw new ValidationFailureException("camt.056 request body is required");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new ValidationFailureException("camt.056 content type is required");
        }
        var normalizedContentType = contentType.split(";", 2)[0].trim().toLowerCase();
        if (!SUPPORTED_CONTENT_TYPES.contains(normalizedContentType)) {
            throw new ValidationFailureException("Unsupported camt.056 content type");
        }
    }

    private FiPaymentId parsePaymentId(String paymentIdValue) {
        if (paymentIdValue == null || paymentIdValue.isBlank()) {
            throw new ValidationFailureException("paymentId must be a UUID string");
        }
        try {
            return new FiPaymentId(UUID.fromString(paymentIdValue));
        } catch (IllegalArgumentException exception) {
            throw new ValidationFailureException("paymentId must be a UUID string");
        }
    }

    private void validateRecallEligibility(FiPaymentRecord payment) {
        if (payment.status() != FiPaymentStatus.SETTLED && payment.status() != FiPaymentStatus.PROCESSING) {
            throw new ValidationFailureException("Only SETTLED or PROCESSING FI payments can be recalled");
        }
    }

    private void validateRecallRequest(
            Camt056RecallRequestParser.ParsedRecallRequest recallRequest,
            FiPaymentRecord payment) {
        if (recallRequest.messageId() == null || recallRequest.messageId().isBlank()) {
            throw new ValidationFailureException("camt.056 recall message ID is required");
        }
        if (recallRequest.caseId() == null || recallRequest.caseId().isBlank()) {
            throw new ValidationFailureException("camt.056 case ID is required");
        }
        if (recallRequest.originalPaymentReference() == null || recallRequest.originalPaymentReference().isBlank()) {
            throw new ValidationFailureException("camt.056 original payment reference is required");
        }
        if (!payment.identifiers().originalPaymentReference().equals(recallRequest.originalPaymentReference())) {
            throw new ValidationFailureException(
                    "camt.056 original payment reference does not match target FI payment");
        }
        if (recallRequest.reasonCode() == null || !SUPPORTED_RECALL_REASONS.contains(recallRequest.reasonCode())) {
            throw new ValidationFailureException("Unsupported camt.056 recall reason");
        }
    }

    private String requireScenario(String mockScenario) {
        if (mockScenario == null || mockScenario.isBlank()) {
            throw new ValidationFailureException("Recall investigation simulator scenario is required");
        }
        return mockScenario;
    }

    private String replayOrConflict(IdempotencyRecord existing, String fingerprint) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException("Idempotency key was reused with a different recall request");
        }
        if (existing.originalResponseXml() == null || existing.originalResponseXml().isBlank()) {
            throw new IdempotencyConflictException("Idempotency record does not contain a camt.029 response");
        }
        return existing.originalResponseXml();
    }

    private RecallInvestigationRecord toRecord(
            FiPaymentRecord payment,
            Camt056RecallRequestParser.ParsedRecallRequest recallRequest,
            RecallInvestigationOutcome outcome,
            AuthorizationContext authorizationContext,
            Instant now) {
        return new RecallInvestigationRecord(
                new RecallInvestigationId(UUID.randomUUID()),
                payment.paymentId(),
                authorizationContext.clientId(),
                recallRequest.messageId(),
                recallRequest.caseId(),
                recallRequest.originalPaymentReference(),
                outcome.status(),
                outcome.reasonCode(),
                outcome.reasonMessage(),
                payment.correspondentSettlementContext(),
                authorizationContext.correlationId(),
                now,
                now);
    }

    private PaymentStatus toPaymentStatus(RecallInvestigationStatus status) {
        return switch (status) {
            case ACCEPTED -> PaymentStatus.COMPLETED;
            case PENDING -> PaymentStatus.PROCESSING;
            case REJECTED -> PaymentStatus.REJECTED;
        };
    }

    private Object idempotencyLock(String clientId, String idempotencyKey) {
        var hash = Math.floorMod((clientId + ":" + idempotencyKey).hashCode(), idempotencyLocks.length);
        return idempotencyLocks[hash];
    }
}
