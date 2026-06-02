package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.AccountReferenceRequest;
import com.cib.payment.api.api.dto.CreateRtgsPaymentRequest;
import com.cib.payment.api.api.dto.MoneyRequest;
import com.cib.payment.api.api.dto.PaymentLinksResponse;
import com.cib.payment.api.api.dto.PaymentReasonResponse;
import com.cib.payment.api.api.dto.RtgsPaymentResponse;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.IdempotencyRepository;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.port.RtgsPaymentOutcome;
import com.cib.payment.api.application.port.RtgsPaymentRepository;
import com.cib.payment.api.application.port.RtgsPaymentSimulator;
import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentStatus;
import com.cib.payment.api.domain.model.RtgsClientSegment;
import com.cib.payment.api.domain.model.RtgsPaymentId;
import com.cib.payment.api.domain.model.RtgsPaymentRecord;
import com.cib.payment.api.domain.model.RtgsPaymentStatus;
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
public class CreateRtgsPaymentService {
    private static final String DEFAULT_MOCK_SCENARIO = "rtgs_settled";
    private static final String RAIL = "RTGS";
    private static final String USD = "USD";

    private final RtgsPaymentRepository rtgsPaymentRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final RequestFingerprintService fingerprintService;
    private final RtgsPaymentSimulator simulator;
    private final PaymentObservability observability;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Object[] idempotencyLocks;

    @Autowired
    public CreateRtgsPaymentService(
            RtgsPaymentRepository rtgsPaymentRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            RtgsPaymentSimulator simulator,
            PaymentObservability observability) {
        this(rtgsPaymentRepository, idempotencyRepository, fingerprintService, simulator, observability, Clock.systemUTC());
    }

    CreateRtgsPaymentService(
            RtgsPaymentRepository rtgsPaymentRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            RtgsPaymentSimulator simulator) {
        this(
                rtgsPaymentRepository,
                idempotencyRepository,
                fingerprintService,
                simulator,
                PaymentObservability.noop(),
                Clock.systemUTC());
    }

    CreateRtgsPaymentService(
            RtgsPaymentRepository rtgsPaymentRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            RtgsPaymentSimulator simulator,
            Clock clock) {
        this(
                rtgsPaymentRepository,
                idempotencyRepository,
                fingerprintService,
                simulator,
                PaymentObservability.noop(),
                clock);
    }

    CreateRtgsPaymentService(
            RtgsPaymentRepository rtgsPaymentRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            RtgsPaymentSimulator simulator,
            PaymentObservability observability,
            Clock clock) {
        this.rtgsPaymentRepository = rtgsPaymentRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.fingerprintService = fingerprintService;
        this.simulator = simulator;
        this.observability = observability;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.clock = clock;
        this.idempotencyLocks = IntStream.range(0, 256).mapToObj(index -> new Object()).toArray();
    }

    public RtgsPaymentResponse create(
            CreateRtgsPaymentRequest request,
            AuthorizationContext authorizationContext,
            String idempotencyKey,
            String mockScenario) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationFailureException("Idempotency-Key is required");
        }
        validateRtgsRules(request);

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
                    new PaymentId(completedRecord.paymentId().value()),
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

            rtgsPaymentRepository.save(completedRecord);
            observability.rtgsPaymentAccepted(completedRecord, authorizationContext);
            return response;
        }
    }

    private void validateRtgsRules(CreateRtgsPaymentRequest request) {
        if (request == null) {
            throw new ValidationFailureException("RTGS payment request is required");
        }
        if (!request.isCorporateSegment() && !request.isFiSegment()) {
            throw new ValidationFailureException("RTGS clientSegment must be CORPORATE or FI");
        }
        if (request.isCorporateSegment() && !request.hasRequiredCorporateParties()) {
            throw new ValidationFailureException("Corporate RTGS requires debtor and creditor account references");
        }
        if (request.isFiSegment() && !request.hasRequiredFiAgents()) {
            throw new ValidationFailureException("FI RTGS requires instructingAgentBic and instructedAgentBic");
        }
        if (!request.hasUsdAmount()) {
            throw new ValidationFailureException("RTGS payments must be denominated in USD");
        }
    }

    private RtgsPaymentRecord acceptedRecord(
            CreateRtgsPaymentRequest request,
            AuthorizationContext authorizationContext,
            Instant now) {
        var clientSegment = RtgsClientSegment.valueOf(request.clientSegment());
        return new RtgsPaymentRecord(
                new RtgsPaymentId(UUID.randomUUID()),
                authorizationContext.clientId(),
                clientSegment,
                request.paymentReference(),
                toParties(request),
                toMoney(request.amount()),
                request.requestedSettlementDate(),
                request.settlementPriority(),
                request.purpose(),
                RtgsPaymentStatus.ACCEPTED_FOR_SETTLEMENT,
                false,
                authorizationContext.correlationId(),
                now,
                now,
                Optional.empty());
    }

    private RtgsPaymentRecord applyOutcome(
            RtgsPaymentRecord acceptedRecord,
            RtgsPaymentOutcome outcome,
            Instant now) {
        return new RtgsPaymentRecord(
                acceptedRecord.paymentId(),
                acceptedRecord.ownerClientId(),
                acceptedRecord.clientSegment(),
                acceptedRecord.paymentReference(),
                acceptedRecord.parties(),
                acceptedRecord.settlementAmount(),
                acceptedRecord.requestedSettlementDate(),
                acceptedRecord.settlementPriority(),
                acceptedRecord.purpose(),
                outcome.status(),
                outcome.settlementFinality(),
                acceptedRecord.correlationId(),
                acceptedRecord.createdAt(),
                now,
                outcome.reason());
    }

    private RtgsPaymentRecord.RtgsParties toParties(CreateRtgsPaymentRequest request) {
        if (request.isCorporateSegment()) {
            return new RtgsPaymentRecord.CorporateParties(
                    toAccount(request.debtorAccount()),
                    toAccount(request.creditorAccount()));
        }
        return new RtgsPaymentRecord.FiParties(
                request.instructingAgentBic(),
                request.instructedAgentBic());
    }

    private RtgsPaymentResponse replayOrConflict(IdempotencyRecord existing, String fingerprint) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new IdempotencyConflictException("Idempotency key was reused with a different RTGS payment request");
        }
        if (existing.originalResponseJson() == null || existing.originalResponseJson().isBlank()) {
            throw new IdempotencyConflictException("Idempotency record does not contain an RTGS payment response");
        }
        try {
            return objectMapper.readValue(existing.originalResponseJson(), RtgsPaymentResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to replay RTGS payment response", exception);
        }
    }

    private String writeResponseJson(RtgsPaymentResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to store RTGS payment response", exception);
        }
    }

    private RtgsPaymentResponse toResponse(RtgsPaymentRecord record) {
        var paymentId = record.paymentId().value().toString();
        return new RtgsPaymentResponse(
                paymentId,
                RAIL,
                record.clientSegment().name(),
                record.status().name(),
                record.settlementFinality(),
                record.createdAt(),
                record.updatedAt(),
                record.correlationId().value(),
                record.reason().map(this::toReasonResponse).orElse(null),
                new PaymentLinksResponse(null, "/v1/rtgs-payments/" + paymentId));
    }

    private AccountReference toAccount(AccountReferenceRequest account) {
        return new AccountReference(account.bankCode(), account.accountNumber(), account.accountName());
    }

    private Money toMoney(MoneyRequest money) {
        return new Money(money.currency(), money.value());
    }

    private PaymentReasonResponse toReasonResponse(PaymentReason reason) {
        return new PaymentReasonResponse(reason.code(), reason.message());
    }

    private PaymentStatus toPaymentStatus(RtgsPaymentStatus status) {
        return switch (status) {
            case SETTLED -> PaymentStatus.COMPLETED;
            case ACCEPTED_FOR_SETTLEMENT, QUEUED_FOR_LIQUIDITY -> PaymentStatus.PROCESSING;
            case REJECTED -> PaymentStatus.REJECTED;
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
