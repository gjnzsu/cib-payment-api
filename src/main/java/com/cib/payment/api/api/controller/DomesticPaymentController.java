package com.cib.payment.api.api.controller;

import com.cib.payment.api.api.dto.CreateDomesticPaymentRequest;
import com.cib.payment.api.api.dto.PaymentResponse;
import com.cib.payment.api.api.dto.PaymentStatusResponse;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.service.AuthorizationContextService;
import com.cib.payment.api.application.service.CreateDomesticPaymentService;
import com.cib.payment.api.application.service.GetDomesticPaymentStatusService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
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
    private final CreateDomesticPaymentService createDomesticPaymentService;
    private final GetDomesticPaymentStatusService getDomesticPaymentStatusService;
    private final AuthorizationContextService authorizationContextService;
    private final PaymentObservability observability;

    public DomesticPaymentController(
            CreateDomesticPaymentService createDomesticPaymentService,
            GetDomesticPaymentStatusService getDomesticPaymentStatusService,
            AuthorizationContextService authorizationContextService,
            PaymentObservability observability) {
        this.createDomesticPaymentService = createDomesticPaymentService;
        this.getDomesticPaymentStatusService = getDomesticPaymentStatusService;
        this.authorizationContextService = authorizationContextService;
        this.observability = observability;
    }

    @PostMapping
    ResponseEntity<PaymentResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String mockScenario,
            @Valid @RequestBody CreateDomesticPaymentRequest request) {
        var startedAt = Instant.now();
        var response = createDomesticPaymentService.create(
                request,
                authorizationContextService.from(jwt, servletRequest),
                idempotencyKey,
                mockScenario);
        observability.apiRequest("create", "accepted", Duration.between(startedAt, Instant.now()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{paymentId}")
    ResponseEntity<PaymentStatusResponse> getStatus(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @PathVariable String paymentId) {
        var startedAt = Instant.now();
        var response = getDomesticPaymentStatusService.getStatus(
                paymentId,
                authorizationContextService.from(jwt, servletRequest));
        observability.apiRequest("status", "ok", Duration.between(startedAt, Instant.now()));
        return ResponseEntity.ok(response);
    }
}
