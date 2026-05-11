package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.AccountReferenceRequest;
import com.cib.payment.api.api.dto.CreateDomesticPaymentRequest;
import com.cib.payment.api.api.dto.MoneyRequest;
import com.cib.payment.api.api.dto.PaymentLinksResponse;
import com.cib.payment.api.api.dto.PaymentResponse;
import com.cib.payment.api.application.exception.IdempotencyConflictException;
import com.cib.payment.api.application.exception.SemanticPaymentException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.DownstreamPaymentProcessor;
import com.cib.payment.api.application.port.IdempotencyRepository;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.port.PaymentStatusRepository;
import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.IdempotencyRecord;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentInstruction;
import com.cib.payment.api.domain.model.PaymentRecord;
import com.cib.payment.api.domain.model.PaymentStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CreateDomesticPaymentService {
    private final PaymentStatusRepository paymentStatusRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final RequestFingerprintService fingerprintService;
    private final DownstreamPaymentProcessor downstreamPaymentProcessor;
    private final PaymentObservability observability;
    private final String domesticCurrency;
    private final Clock clock;

    @Autowired
    public CreateDomesticPaymentService(
            PaymentStatusRepository paymentStatusRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            DownstreamPaymentProcessor downstreamPaymentProcessor,
            PaymentObservability observability,
            @Value("${payment-api.domestic-currency}") String domesticCurrency) {
        this(paymentStatusRepository, idempotencyRepository, fingerprintService, downstreamPaymentProcessor, observability, domesticCurrency, Clock.systemUTC());
    }

    CreateDomesticPaymentService(
            PaymentStatusRepository paymentStatusRepository,
            IdempotencyRepository idempotencyRepository,
            RequestFingerprintService fingerprintService,
            DownstreamPaymentProcessor downstreamPaymentProcessor,
            PaymentObservability observability,
            String domesticCurrency,
            Clock clock) {
        this.paymentStatusRepository = paymentStatusRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.fingerprintService = fingerprintService;
        this.downstreamPaymentProcessor = downstreamPaymentProcessor;
        this.observability = observability;
        this.domesticCurrency = domesticCurrency;
        this.clock = clock;
    }

    public PaymentResponse create(
            CreateDomesticPaymentRequest request,
            AuthorizationContext authorizationContext,
            String idempotencyKey,
            String mockScenario) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ValidationFailureException("Idempotency-Key is required");
        }
        if (!domesticCurrency.equals(request.amount().currency())) {
            throw new SemanticPaymentException("Unsupported domestic currency: " + request.amount().currency());
        }

        var normalizedScenario = mockScenario == null || mockScenario.isBlank() ? "success" : mockScenario;
        var fingerprint = fingerprintService.fingerprint(
                authorizationContext.clientId(),
                request,
                Map.of("mockScenario", normalizedScenario));

        var existing = idempotencyRepository.find(authorizationContext.clientId(), idempotencyKey);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), fingerprint, authorizationContext.correlationId());
        }

        var now = Instant.now(clock);
        var paymentId = new PaymentId(UUID.randomUUID());
        var instruction = toInstruction(request);
        var acceptedRecord = new PaymentRecord(
                paymentId,
                authorizationContext.clientId(),
                instruction,
                PaymentStatus.ACCEPTED,
                now,
                now,
                authorizationContext.correlationId(),
                Optional.empty());

        var idempotencyRecord = new IdempotencyRecord(
                authorizationContext.clientId(),
                idempotencyKey,
                fingerprint,
                paymentId,
                PaymentStatus.ACCEPTED,
                authorizationContext.correlationId(),
                now,
                now);
        var storedIdempotencyRecord = idempotencyRepository.saveIfAbsent(idempotencyRecord);
        if (!storedIdempotencyRecord.equals(idempotencyRecord)) {
            return replayOrConflict(storedIdempotencyRecord, fingerprint, authorizationContext.correlationId());
        }

        paymentStatusRepository.save(acceptedRecord);
        observability.paymentCreationAccepted(request, authorizationContext, acceptedRecord);

        var outcome = downstreamPaymentProcessor.process(
                instruction,
                authorizationContext,
                authorizationContext.correlationId(),
                normalizedScenario);
        paymentStatusRepository.updateStatus(paymentId, outcome.status(), outcome.reason().orElse(null));

        return toResponse(idempotencyRecord);
    }

    private PaymentResponse replayOrConflict(IdempotencyRecord existing, String fingerprint, com.cib.payment.api.domain.model.CorrelationId correlationId) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            observability.idempotencyConflict(existing, correlationId);
            throw new IdempotencyConflictException("Idempotency key was reused with a different request body");
        }
        observability.idempotencyReplay(existing);
        return toResponse(existing);
    }

    private PaymentResponse toResponse(IdempotencyRecord record) {
        var paymentId = record.paymentId().value().toString();
        return new PaymentResponse(
                paymentId,
                record.status().name(),
                record.createdAt(),
                record.updatedAt(),
                record.correlationId().value(),
                new PaymentLinksResponse(null, "/v1/domestic-payments/" + paymentId));
    }

    private PaymentInstruction toInstruction(CreateDomesticPaymentRequest request) {
        return new PaymentInstruction(
                toAccount(request.debtorAccount()),
                toAccount(request.creditorAccount()),
                toMoney(request.amount()),
                request.paymentReference(),
                request.remittanceInformation(),
                request.requestedExecutionDate());
    }

    private AccountReference toAccount(AccountReferenceRequest account) {
        return new AccountReference(account.bankCode(), account.accountNumber(), account.accountName());
    }

    private Money toMoney(MoneyRequest money) {
        return new Money(money.currency(), money.value());
    }
}
