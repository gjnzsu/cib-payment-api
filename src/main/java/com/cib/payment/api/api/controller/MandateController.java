package com.cib.payment.api.api.controller;

import com.cib.payment.api.api.dto.CancelMandateRequest;
import com.cib.payment.api.api.dto.CreateMandateRequest;
import com.cib.payment.api.api.dto.MandateResponse;
import com.cib.payment.api.api.dto.MandateStatusResponse;
import com.cib.payment.api.application.exception.AuthorizationScopeException;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.service.AuthorizationContextService;
import com.cib.payment.api.application.service.CancelMandateService;
import com.cib.payment.api.application.service.CreateMandateService;
import com.cib.payment.api.application.service.GetMandateStatusService;
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
@RequestMapping("/v1/mandates")
public class MandateController {
    private static final String CREATE_SCOPE = "mandates:create";
    private static final String READ_SCOPE = "mandates:read";
    private static final String CANCEL_SCOPE = "mandates:cancel";

    private final CreateMandateService createMandateService;
    private final GetMandateStatusService getMandateStatusService;
    private final CancelMandateService cancelMandateService;
    private final AuthorizationContextService authorizationContextService;
    private final PaymentObservability observability;

    public MandateController(
            CreateMandateService createMandateService,
            GetMandateStatusService getMandateStatusService,
            CancelMandateService cancelMandateService,
            AuthorizationContextService authorizationContextService,
            PaymentObservability observability) {
        this.createMandateService = createMandateService;
        this.getMandateStatusService = getMandateStatusService;
        this.cancelMandateService = cancelMandateService;
        this.authorizationContextService = authorizationContextService;
        this.observability = observability;
    }

    @PostMapping
    ResponseEntity<MandateResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String mockScenario,
            @Valid @RequestBody CreateMandateRequest requestBody) {
        var startedAt = Instant.now();
        var authorizationContext = authorizationContextService.from(jwt, servletRequest);
        requireScope(authorizationContext, CREATE_SCOPE);
        var response = createMandateService.create(requestBody, authorizationContext, idempotencyKey, mockScenario);
        observability.apiRequest("mandate-create", "ok", Duration.between(startedAt, Instant.now()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @GetMapping("/{mandateId}")
    ResponseEntity<MandateStatusResponse> getStatus(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @PathVariable String mandateId) {
        var startedAt = Instant.now();
        var authorizationContext = authorizationContextService.from(jwt, servletRequest);
        requireScope(authorizationContext, READ_SCOPE);
        var response = getMandateStatusService.getStatus(mandateId, authorizationContext);
        observability.apiRequest("mandate-status", "ok", Duration.between(startedAt, Instant.now()));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @PostMapping("/{mandateId}/cancel")
    ResponseEntity<MandateResponse> cancel(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @PathVariable String mandateId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CancelMandateRequest requestBody) {
        var startedAt = Instant.now();
        var authorizationContext = authorizationContextService.from(jwt, servletRequest);
        requireScope(authorizationContext, CANCEL_SCOPE);
        var response = cancelMandateService.cancel(mandateId, requestBody, authorizationContext, idempotencyKey);
        observability.apiRequest("mandate-cancel", "ok", Duration.between(startedAt, Instant.now()));
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
