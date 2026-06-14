package com.cib.payment.api.application.service;

import com.cib.payment.api.api.dto.CreatePaymentRailRecommendationRequest;
import com.cib.payment.api.api.dto.PaymentRailRecommendationResponse;
import com.cib.payment.api.api.dto.RecommendationNextApiGuidanceResponse;
import com.cib.payment.api.api.dto.RecommendationOptionResponse;
import com.cib.payment.api.api.dto.RecommendationTradeoffResponse;
import com.cib.payment.api.api.dto.RecommendationWarningResponse;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.application.port.PaymentRailRecommendationRules;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.PaymentArrangement;
import com.cib.payment.api.domain.model.PaymentRailRecommendation;
import com.cib.payment.api.domain.model.RecommendationAmountSummary;
import com.cib.payment.api.domain.model.RecommendationClientSegment;
import com.cib.payment.api.domain.model.RecommendationCostSensitivity;
import com.cib.payment.api.domain.model.RecommendationDebtorAccountProfile;
import com.cib.payment.api.domain.model.RecommendationIntent;
import com.cib.payment.api.domain.model.RecommendationUrgency;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class CreatePaymentRailRecommendationService {
    private final PaymentRailRecommendationRules recommendationRules;
    private final PaymentObservability observability;

    public CreatePaymentRailRecommendationService(
            PaymentRailRecommendationRules recommendationRules,
            PaymentObservability observability) {
        this.recommendationRules = recommendationRules;
        this.observability = observability;
    }

    public PaymentRailRecommendationResponse recommend(
            CreatePaymentRailRecommendationRequest request,
            AuthorizationContext authorizationContext) {
        var recommendation = recommendationRules.recommend(toIntent(request, authorizationContext));
        observability.paymentRailRecommendationGenerated(recommendation, authorizationContext);
        return toResponse(recommendation);
    }

    private RecommendationIntent toIntent(
            CreatePaymentRailRecommendationRequest request,
            AuthorizationContext authorizationContext) {
        return RecommendationIntent.builder()
                .clientSegment(RecommendationClientSegment.valueOf(request.clientSegment()))
                .paymentPurpose(request.paymentPurpose())
                .paymentCount(request.paymentCount())
                .amountSummary(new RecommendationAmountSummary(
                        request.amountSummary().currency(),
                        request.amountSummary().totalAmount(),
                        Optional.ofNullable(request.amountSummary().maxSingleAmount())))
                .debtorCountry(request.debtorCountry())
                .creditorCountry(request.creditorCountry())
                .urgency(RecommendationUrgency.valueOf(request.urgency()))
                .creditorType(request.creditorType())
                .requiresFinality(request.requiresFinalityValue())
                .batchPreferred(request.batchPreferredValue())
                .costSensitivity(request.costSensitivity() == null
                        ? RecommendationCostSensitivity.MEDIUM
                        : RecommendationCostSensitivity.valueOf(request.costSensitivity()))
                .fiToFi(request.fiToFiValue())
                .arrangementPreference(request.arrangementPreference() == null
                        ? null
                        : PaymentArrangement.valueOf(request.arrangementPreference()))
                .debtorAccountProfile(new RecommendationDebtorAccountProfile(request.debtorAccountCount()))
                .correlationId(authorizationContext.correlationId())
                .build();
    }

    private PaymentRailRecommendationResponse toResponse(PaymentRailRecommendation recommendation) {
        return new PaymentRailRecommendationResponse(
                recommendation.recommendationId().value().toString(),
                recommendation.recommendationStatus().name(),
                recommendation.recommendedOption().map(this::toOption).orElse(null),
                recommendation.confidenceLevel().name(),
                recommendation.decisionSummary(),
                recommendation.matchedFactors().stream().map(Enum::name).toList(),
                recommendation.warnings().stream()
                        .map(warning -> new RecommendationWarningResponse(warning.code(), warning.message()))
                        .toList(),
                recommendation.alternatives().stream().map(this::toOption).toList(),
                recommendation.tradeoffs().stream()
                        .map(tradeoff -> new RecommendationTradeoffResponse(
                                tradeoff.rail().name(),
                                tradeoff.arrangement().name(),
                                tradeoff.speed().name(),
                                tradeoff.cost().name(),
                                tradeoff.finality().name(),
                                tradeoff.intentFit().name(),
                                tradeoff.intentFitReason(),
                                tradeoff.summary()))
                        .toList(),
                recommendation.nextApiGuidance().map(this::toGuidance).orElse(null),
                recommendation.correlationId().value());
    }

    private RecommendationOptionResponse toOption(com.cib.payment.api.domain.model.RecommendationOption option) {
        return new RecommendationOptionResponse(
                option.rail().name(),
                option.arrangement().name(),
                option.clientSegment().name(),
                option.reasonCode());
    }

    private RecommendationNextApiGuidanceResponse toGuidance(
            com.cib.payment.api.domain.model.RecommendationNextApiGuidance guidance) {
        return new RecommendationNextApiGuidanceResponse(
                guidance.method(),
                guidance.endpoint(),
                guidance.requiredScopes(),
                guidance.requiredHeaders(),
                guidance.payloadFormat());
    }
}
