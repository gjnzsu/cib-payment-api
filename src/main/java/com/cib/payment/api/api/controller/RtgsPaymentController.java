package com.cib.payment.api.api.controller;

import com.cib.payment.api.api.dto.CreateRtgsPaymentRequest;
import com.cib.payment.api.api.dto.RtgsPaymentResponse;
import com.cib.payment.api.api.dto.RtgsPaymentStatusResponse;
import com.cib.payment.api.application.exception.AuthorizationScopeException;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.service.AuthorizationContextService;
import com.cib.payment.api.application.service.CreateRtgsPaymentService;
import com.cib.payment.api.application.service.GetRtgsPaymentStatusService;
import com.cib.payment.api.domain.model.AuthorizationContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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
@RequestMapping("/v1/rtgs-payments")
public class RtgsPaymentController {
    private static final String CREATE_SCOPE = "rtgs-payments:create";
    private static final String READ_SCOPE = "rtgs-payments:read";

    private final CreateRtgsPaymentService createRtgsPaymentService;
    private final GetRtgsPaymentStatusService getRtgsPaymentStatusService;
    private final AuthorizationContextService authorizationContextService;
    private final PaymentObservability observability;

    public RtgsPaymentController(
            CreateRtgsPaymentService createRtgsPaymentService,
            GetRtgsPaymentStatusService getRtgsPaymentStatusService,
            AuthorizationContextService authorizationContextService,
            PaymentObservability observability) {
        this.createRtgsPaymentService = createRtgsPaymentService;
        this.getRtgsPaymentStatusService = getRtgsPaymentStatusService;
        this.authorizationContextService = authorizationContextService;
        this.observability = observability;
    }

    @PostMapping
    ResponseEntity<RtgsPaymentResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String mockScenario,
            @Valid @RequestBody CreateRtgsPaymentRequest requestBody) {
        var startedAt = Instant.now();
        var authorizationContext = authorizationContextService.from(jwt, servletRequest);
        requireScope(authorizationContext, CREATE_SCOPE);
        var response = createRtgsPaymentService.create(
                requestBody,
                authorizationContext,
                idempotencyKey,
                mockScenario);
        observability.apiRequest("rtgs-create", "ok", Duration.between(startedAt, Instant.now()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @GetMapping("/{paymentId}")
    ResponseEntity<RtgsPaymentStatusResponse> getStatus(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @PathVariable String paymentId) {
        var startedAt = Instant.now();
        var authorizationContext = authorizationContextService.from(jwt, servletRequest);
        requireScope(authorizationContext, READ_SCOPE);
        var response = getRtgsPaymentStatusService.getStatus(
                paymentId,
                authorizationContext);
        observability.apiRequest("rtgs-status", "ok", Duration.between(startedAt, Instant.now()));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    private void requireScope(AuthorizationContext authorizationContext, String requiredScope) {
        if (!authorizationContext.scopes().contains(requiredScope)) {
            throw new AuthorizationScopeException("Required scope is missing");
        }
    }
}
