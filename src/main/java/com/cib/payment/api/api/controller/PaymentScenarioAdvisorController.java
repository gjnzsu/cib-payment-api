package com.cib.payment.api.api.controller;

import com.cib.payment.api.api.CorrelationIdFilter;
import com.cib.payment.api.api.dto.*;
import com.cib.payment.api.application.exception.AdvisorScenarioNotFoundException;
import com.cib.payment.api.application.service.PaymentScenarioAdvisorCatalogService;
import com.cib.payment.api.domain.model.AdvisorRecommendationSummary;
import com.cib.payment.api.domain.model.AdvisorScenario;
import com.cib.payment.api.domain.model.AdvisorSimulationPlan;
import com.cib.payment.api.domain.model.RecommendationIntent;
import java.math.BigDecimal;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/v1/payment-scenario-advisor")
public class PaymentScenarioAdvisorController {
    private final PaymentScenarioAdvisorCatalogService catalogService;

    public PaymentScenarioAdvisorController(PaymentScenarioAdvisorCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/scenarios")
    ResponseEntity<AdvisorScenarioCatalogResponse> scenarios(HttpServletRequest request) {
        var scenarios = catalogService.listScenarios().stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AdvisorScenarioCatalogResponse(scenarios, correlationId(request)));
    }

    @GetMapping("/scenarios/{scenarioId}")
    ResponseEntity<AdvisorScenarioDetailResponse> scenario(
            @PathVariable String scenarioId,
            HttpServletRequest request) {
        var scenario = catalogService.getScenario(scenarioId)
                .orElseThrow(AdvisorScenarioNotFoundException::new);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(toDetail(scenario, correlationId(request)));
    }

    private AdvisorScenarioSummaryResponse toSummary(AdvisorScenario scenario) {
        return new AdvisorScenarioSummaryResponse(
                scenario.scenarioId().value(),
                scenario.businessLabel(),
                scenario.businessDescription(),
                scenario.recommendation().rail().name(),
                scenario.recommendation().arrangement().name(),
                scenario.simulatorOnly(),
                scenario.requiresUserConfirmation());
    }

    private AdvisorScenarioDetailResponse toDetail(AdvisorScenario scenario, String correlationId) {
        return new AdvisorScenarioDetailResponse(
                scenario.scenarioId().value(),
                scenario.businessLabel(),
                scenario.businessDescription(),
                scenario.simulatorOnly(),
                scenario.requiresUserConfirmation(),
                toIntent(scenario.recommendationIntent()),
                toRecommendation(scenario.recommendation()),
                toPlan(scenario.simulationPlan()),
                new AdvisorFeedbackReportResponse(
                        scenario.feedbackReport().validationStatus(),
                        scenario.feedbackReport().expectedOutcome(),
                        scenario.feedbackReport().businessConclusion(),
                        scenario.feedbackReport().nextStep()),
                correlationId);
    }

    private AdvisorRecommendationIntentResponse toIntent(RecommendationIntent intent) {
        return new AdvisorRecommendationIntentResponse(
                intent.clientSegment().name(),
                intent.paymentPurpose(),
                intent.paymentCount(),
                new AdvisorAmountSummaryResponse(
                        intent.amountSummary().currency(),
                        decimal(intent.amountSummary().totalAmount()),
                        intent.amountSummary().maxSingleAmount().map(this::decimal).orElse(null)),
                intent.debtorCountry(),
                intent.creditorCountry(),
                intent.urgency().name(),
                intent.creditorType(),
                intent.requiresFinality(),
                intent.batchPreferred(),
                intent.costSensitivity().name(),
                intent.fiToFi(),
                intent.arrangementPreference() == null ? null : intent.arrangementPreference().name());
    }

    private AdvisorRecommendationSummaryResponse toRecommendation(AdvisorRecommendationSummary recommendation) {
        return new AdvisorRecommendationSummaryResponse(
                recommendation.rail().name(),
                recommendation.arrangement().name(),
                recommendation.clientSegment().name(),
                recommendation.confidenceLevel().name(),
                recommendation.reasonCode(),
                recommendation.summary(),
                recommendation.matchedFactors(),
                recommendation.tradeoffs());
    }

    private AdvisorSimulationPlanResponse toPlan(AdvisorSimulationPlan plan) {
        return new AdvisorSimulationPlanResponse(
                plan.method(),
                plan.endpoint(),
                plan.requiredScopes(),
                plan.requiredHeaders(),
                plan.idempotencyRequired(),
                plan.payloadFormat(),
                plan.mockScenario(),
                plan.statusEndpointTemplate(),
                plan.expectedStatus(),
                plan.simulatorOnly(),
                plan.requiresUserConfirmation());
    }

    private String correlationId(HttpServletRequest request) {
        var value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME);
        return value == null ? null : value.toString();
    }

    private String decimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
