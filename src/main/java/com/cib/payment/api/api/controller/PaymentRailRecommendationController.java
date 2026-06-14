package com.cib.payment.api.api.controller;

import com.cib.payment.api.api.dto.CreatePaymentRailRecommendationRequest;
import com.cib.payment.api.api.dto.PaymentRailRecommendationResponse;
import com.cib.payment.api.application.exception.AuthorizationScopeException;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.service.AuthorizationContextService;
import com.cib.payment.api.application.service.CreatePaymentRailRecommendationService;
import com.cib.payment.api.domain.model.AuthorizationContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/payment-rail-recommendations")
public class PaymentRailRecommendationController {
    private static final String CREATE_SCOPE = "payment-rail-recommendations:create";

    private final CreatePaymentRailRecommendationService recommendationService;
    private final AuthorizationContextService authorizationContextService;
    private final PaymentObservability observability;

    public PaymentRailRecommendationController(
            CreatePaymentRailRecommendationService recommendationService,
            AuthorizationContextService authorizationContextService,
            PaymentObservability observability) {
        this.recommendationService = recommendationService;
        this.authorizationContextService = authorizationContextService;
        this.observability = observability;
    }

    @PostMapping
    ResponseEntity<PaymentRailRecommendationResponse> recommend(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest,
            @Valid @RequestBody CreatePaymentRailRecommendationRequest requestBody) {
        var startedAt = Instant.now();
        var authorizationContext = authorizationContextService.from(jwt, servletRequest);
        requireScope(authorizationContext, CREATE_SCOPE);
        var response = recommendationService.recommend(requestBody, authorizationContext);
        observability.apiRequest("payment-rail-recommendation", "ok", Duration.between(startedAt, Instant.now()));
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
