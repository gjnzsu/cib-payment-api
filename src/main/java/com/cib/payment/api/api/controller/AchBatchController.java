package com.cib.payment.api.api.controller;

import com.cib.payment.api.api.dto.AchBatchResponse;
import com.cib.payment.api.api.dto.AchBatchStatusResponse;
import com.cib.payment.api.api.dto.CreateAchBatchRequest;
import com.cib.payment.api.application.exception.AuthorizationScopeException;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.service.AuthorizationContextService;
import com.cib.payment.api.application.service.CreateAchBatchService;
import com.cib.payment.api.application.service.GetAchBatchStatusService;
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
@RequestMapping("/v1/ach-batches")
public class AchBatchController {
    private static final String CREATE_SCOPE = "ach-batches:create";
    private static final String READ_SCOPE = "ach-batches:read";

    private final CreateAchBatchService createAchBatchService;
    private final GetAchBatchStatusService getAchBatchStatusService;
    private final AuthorizationContextService authorizationContextService;
    private final PaymentObservability observability;

    public AchBatchController(
            CreateAchBatchService createAchBatchService,
            GetAchBatchStatusService getAchBatchStatusService,
            AuthorizationContextService authorizationContextService,
            PaymentObservability observability) {
        this.createAchBatchService = createAchBatchService;
        this.getAchBatchStatusService = getAchBatchStatusService;
        this.authorizationContextService = authorizationContextService;
        this.observability = observability;
    }

    @PostMapping
    ResponseEntity<AchBatchResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String mockScenario,
            @Valid @RequestBody CreateAchBatchRequest requestBody) {
        var startedAt = Instant.now();
        var authorizationContext = authorizationContextService.from(jwt, servletRequest);
        requireScope(authorizationContext, CREATE_SCOPE);
        var response = createAchBatchService.create(
                requestBody,
                authorizationContext,
                idempotencyKey,
                mockScenario);
        observability.apiRequest("ach-create", "ok", Duration.between(startedAt, Instant.now()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @GetMapping("/{batchId}")
    ResponseEntity<AchBatchStatusResponse> getStatus(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @PathVariable String batchId) {
        var startedAt = Instant.now();
        var authorizationContext = authorizationContextService.from(jwt, servletRequest);
        requireScope(authorizationContext, READ_SCOPE);
        var response = getAchBatchStatusService.getStatus(
                batchId,
                authorizationContext);
        observability.apiRequest("ach-status", "ok", Duration.between(startedAt, Instant.now()));
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
