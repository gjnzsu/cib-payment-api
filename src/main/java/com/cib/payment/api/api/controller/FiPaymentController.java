package com.cib.payment.api.api.controller;

import com.cib.payment.api.api.dto.FiPaymentAcknowledgementResponse;
import com.cib.payment.api.api.dto.FiPaymentStatusResponse;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.service.AuthorizationContextService;
import com.cib.payment.api.application.service.CreateFiPaymentService;
import com.cib.payment.api.application.service.CreateRecallInvestigationService;
import com.cib.payment.api.application.service.GetFiPaymentStatusService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/fi-payments")
public class FiPaymentController {
    private static final MediaType CAMT_029_MEDIA_TYPE = MediaType.valueOf("application/camt.029+xml");

    private final CreateFiPaymentService createFiPaymentService;
    private final GetFiPaymentStatusService getFiPaymentStatusService;
    private final CreateRecallInvestigationService createRecallInvestigationService;
    private final AuthorizationContextService authorizationContextService;
    private final PaymentObservability observability;

    public FiPaymentController(
            CreateFiPaymentService createFiPaymentService,
            GetFiPaymentStatusService getFiPaymentStatusService,
            CreateRecallInvestigationService createRecallInvestigationService,
            AuthorizationContextService authorizationContextService,
            PaymentObservability observability) {
        this.createFiPaymentService = createFiPaymentService;
        this.getFiPaymentStatusService = getFiPaymentStatusService;
        this.createRecallInvestigationService = createRecallInvestigationService;
        this.authorizationContextService = authorizationContextService;
        this.observability = observability;
    }

    @PostMapping
    ResponseEntity<FiPaymentAcknowledgementResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String mockScenario,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody(required = false) String requestBody) {
        var startedAt = Instant.now();
        var response = createFiPaymentService.create(
                requestBody,
                contentType,
                authorizationContextService.from(jwt, servletRequest),
                idempotencyKey,
                mockScenario);
        observability.apiRequest("fi-create", "ok", Duration.between(startedAt, Instant.now()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @GetMapping("/{paymentId}")
    ResponseEntity<FiPaymentStatusResponse> getStatus(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @PathVariable String paymentId) {
        var startedAt = Instant.now();
        var response = getFiPaymentStatusService.getStatus(
                paymentId,
                authorizationContextService.from(jwt, servletRequest));
        observability.apiRequest("fi-status", "ok", Duration.between(startedAt, Instant.now()));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @PostMapping("/{paymentId}/recall-requests")
    ResponseEntity<String> createRecall(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @PathVariable String paymentId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String mockScenario,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody(required = false) String requestBody) {
        var startedAt = Instant.now();
        var response = createRecallInvestigationService.create(
                paymentId,
                requestBody,
                contentType,
                authorizationContextService.from(jwt, servletRequest),
                idempotencyKey,
                mockScenario);
        observability.apiRequest("fi-recall", "ok", Duration.between(startedAt, Instant.now()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .contentType(CAMT_029_MEDIA_TYPE)
                .body(response);
    }
}
