package com.cib.payment.api.api.controller;

import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.service.AuthorizationContextService;
import com.cib.payment.api.application.service.CreateIsoDomesticPaymentService;
import com.cib.payment.api.application.service.GetIsoPaymentStatusService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
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
@RequestMapping("/v1/domestic-payments")
public class DomesticPaymentController {
    private static final MediaType PAIN_002_MEDIA_TYPE = MediaType.valueOf("application/pain.002+xml");

    private final CreateIsoDomesticPaymentService createIsoDomesticPaymentService;
    private final GetIsoPaymentStatusService getIsoPaymentStatusService;
    private final AuthorizationContextService authorizationContextService;
    private final PaymentObservability observability;

    public DomesticPaymentController(
            CreateIsoDomesticPaymentService createIsoDomesticPaymentService,
            GetIsoPaymentStatusService getIsoPaymentStatusService,
            AuthorizationContextService authorizationContextService,
            PaymentObservability observability) {
        this.createIsoDomesticPaymentService = createIsoDomesticPaymentService;
        this.getIsoPaymentStatusService = getIsoPaymentStatusService;
        this.authorizationContextService = authorizationContextService;
        this.observability = observability;
    }

    @PostMapping
    ResponseEntity<String> create(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String mockScenario,
            @RequestHeader(value = "Content-Type", required = false) String contentType,
            @RequestBody(required = false) String requestBody) {
        var startedAt = Instant.now();
        var response = createIsoDomesticPaymentService.create(
                requestBody,
                contentType,
                authorizationContextService.from(jwt, servletRequest),
                idempotencyKey,
                mockScenario);
        observability.apiRequest("create", "ok", Duration.between(startedAt, Instant.now()));
        return ResponseEntity.ok()
                .contentType(PAIN_002_MEDIA_TYPE)
                .body(response);
    }

    @GetMapping("/{paymentId}")
    ResponseEntity<String> getStatus(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @PathVariable String paymentId) {
        var startedAt = Instant.now();
        var response = getIsoPaymentStatusService.getStatusReport(
                paymentId,
                authorizationContextService.from(jwt, servletRequest));
        observability.apiRequest("status", "ok", Duration.between(startedAt, Instant.now()));
        return ResponseEntity.ok()
                .contentType(PAIN_002_MEDIA_TYPE)
                .body(response);
    }
}
