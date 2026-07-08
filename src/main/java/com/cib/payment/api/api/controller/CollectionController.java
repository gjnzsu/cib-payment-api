package com.cib.payment.api.api.controller;

import com.cib.payment.api.api.dto.CollectionResponse;
import com.cib.payment.api.api.dto.CollectionStatusResponse;
import com.cib.payment.api.api.dto.CreateCollectionRequest;
import com.cib.payment.api.application.exception.AuthorizationScopeException;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.service.AuthorizationContextService;
import com.cib.payment.api.application.service.CreateCollectionService;
import com.cib.payment.api.application.service.GetCollectionStatusService;
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
@RequestMapping("/v1/collections")
public class CollectionController {
    private static final String CREATE_SCOPE = "collections:create";
    private static final String READ_SCOPE = "collections:read";

    private final CreateCollectionService createCollectionService;
    private final GetCollectionStatusService getCollectionStatusService;
    private final AuthorizationContextService authorizationContextService;
    private final PaymentObservability observability;

    public CollectionController(
            CreateCollectionService createCollectionService,
            GetCollectionStatusService getCollectionStatusService,
            AuthorizationContextService authorizationContextService,
            PaymentObservability observability) {
        this.createCollectionService = createCollectionService;
        this.getCollectionStatusService = getCollectionStatusService;
        this.authorizationContextService = authorizationContextService;
        this.observability = observability;
    }

    @PostMapping
    ResponseEntity<CollectionResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Mock-Scenario", required = false) String mockScenario,
            @Valid @RequestBody CreateCollectionRequest requestBody) {
        var startedAt = Instant.now();
        var authorizationContext = authorizationContextService.from(jwt, servletRequest);
        requireScope(authorizationContext, CREATE_SCOPE);
        var response = createCollectionService.create(
                requestBody,
                authorizationContext,
                idempotencyKey,
                mockScenario);
        observability.apiRequest("collection-create", "ok", Duration.between(startedAt, Instant.now()));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    @GetMapping("/{collectionId}")
    ResponseEntity<CollectionStatusResponse> getStatus(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @PathVariable String collectionId) {
        var startedAt = Instant.now();
        var authorizationContext = authorizationContextService.from(jwt, servletRequest);
        requireScope(authorizationContext, READ_SCOPE);
        var response = getCollectionStatusService.getStatus(
                collectionId,
                authorizationContext);
        observability.apiRequest("collection-status", "ok", Duration.between(startedAt, Instant.now()));
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
