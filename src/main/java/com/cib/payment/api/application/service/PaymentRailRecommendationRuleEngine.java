package com.cib.payment.api.application.service;

import com.cib.payment.api.application.port.PaymentRailRecommendationRules;
import com.cib.payment.api.domain.model.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PaymentRailRecommendationRuleEngine implements PaymentRailRecommendationRules {
    private static final BigDecimal SIMULATOR_HIGH_VALUE_THRESHOLD = new BigDecimal("100000.00");

    @Override
    public PaymentRailRecommendation recommend(RecommendationIntent intent) {
        if (!"US".equals(intent.debtorCountry()) || !"US".equals(intent.creditorCountry())) {
            return unsupported(
                    intent,
                    "Cross-border payment intents are outside the simulator recommendation scope.",
                    new RecommendationWarning("CROSS_BORDER_NOT_SUPPORTED", "Only domestic US intents are supported."));
        }
        if (!"USD".equals(intent.amountSummary().currency())) {
            return unsupported(
                    intent,
                    "Non-USD payment intents are outside the simulator recommendation scope.",
                    new RecommendationWarning("NON_USD_NOT_SUPPORTED", "Only USD intents are supported."));
        }

        if (intent.clientSegment() == RecommendationClientSegment.FI
                && intent.arrangementPreference() == PaymentArrangement.CORRESPONDENT_ACCOUNT_PATH) {
            return supported(
                    intent,
                    new RecommendationOption(
                            PaymentRail.FI_CORRESPONDENT,
                            PaymentArrangement.CORRESPONDENT_ACCOUNT_PATH,
                            RecommendationClientSegment.FI,
                            "FI_CORRESPONDENT_ACCOUNT_PATH"),
                    RecommendationConfidence.HIGH,
                    "FI correspondent is recommended because the FI intent prefers correspondent account path.",
                    List.of(
                            RecommendationMatchedFactor.DOMESTIC_USD,
                            RecommendationMatchedFactor.FI_CLIENT,
                            RecommendationMatchedFactor.CORRESPONDENT_ACCOUNT_PATH),
                    List.of(),
                    List.of(alternative(PaymentRail.RTGS, PaymentArrangement.DOMESTIC_INTERBANK_GROSS_SETTLEMENT, RecommendationClientSegment.FI, "FI_DOMESTIC_GROSS_SETTLEMENT")),
                    List.of(correspondentTradeoff()),
                    guidance("POST", "/v1/fi-payments", List.of("fi-payments:create"), "application/pacs.009+xml"));
        }

        if (isBatchSignal(intent)) {
            var warnings = new ArrayList<RecommendationWarning>();
            var alternatives = new ArrayList<RecommendationOption>();
            if (intent.amountSummary().maxSingleAmount().isEmpty()) {
                warnings.add(new RecommendationWarning(
                        "MAX_SINGLE_AMOUNT_NOT_PROVIDED",
                        "Recommendation assumes no individual payment requires high-value gross settlement review."));
            }
            if (hasHighValueSingleAmount(intent) || intent.requiresFinality()) {
                warnings.add(new RecommendationWarning(
                        "BATCH_HIGH_VALUE_ENTRY_REVIEW",
                        "Batch intent contains high-value or finality signals; review RTGS for individual entries."));
                alternatives.add(alternative(
                        PaymentRail.RTGS,
                        PaymentArrangement.DOMESTIC_INTERBANK_GROSS_SETTLEMENT,
                        intent.clientSegment(),
                        "HIGH_VALUE_ENTRY_FINALITY_REVIEW"));
            }
            return supported(
                    intent,
                    new RecommendationOption(
                            PaymentRail.ACH,
                            PaymentArrangement.BATCH_CLEARING_NET_SETTLEMENT,
                            intent.clientSegment(),
                            "BATCH_EFFICIENCY"),
                    RecommendationConfidence.HIGH,
                    "ACH is recommended because the intent is batch-oriented.",
                    batchFactors(intent),
                    warnings,
                    alternatives,
                    List.of(achTradeoff(), rtgsTradeoff(RecommendationIntentFit.MEDIUM)),
                    guidance("POST", "/v1/ach-batches", List.of("ach-batches:create"), "application/json"));
        }

        if (intent.requiresFinality() || isHighValue(intent)) {
            return supported(
                    intent,
                    new RecommendationOption(
                            PaymentRail.RTGS,
                            PaymentArrangement.DOMESTIC_INTERBANK_GROSS_SETTLEMENT,
                            intent.clientSegment(),
                            "HIGH_VALUE_FINALITY"),
                    RecommendationConfidence.HIGH,
                    "RTGS is recommended because the payment is high value or requires finality.",
                    List.of(
                            RecommendationMatchedFactor.DOMESTIC_USD,
                            RecommendationMatchedFactor.HIGH_VALUE,
                            RecommendationMatchedFactor.REQUIRES_FINALITY),
                    List.of(),
                    List.of(alternative(PaymentRail.RTP, PaymentArrangement.DOMESTIC_REAL_TIME_CLEARING, intent.clientSegment(), "IMMEDIATE_LOW_VALUE")),
                    List.of(rtgsTradeoff(RecommendationIntentFit.HIGH), rtpTradeoff(RecommendationIntentFit.LOW)),
                    guidance("POST", "/v1/rtgs-payments", List.of("rtgs-payments:create"), "application/json"));
        }

        if (intent.urgency() == RecommendationUrgency.IMMEDIATE && !isHighValue(intent)) {
            return supported(
                    intent,
                    new RecommendationOption(
                            PaymentRail.RTP,
                            PaymentArrangement.DOMESTIC_REAL_TIME_CLEARING,
                            intent.clientSegment(),
                            "IMMEDIATE_LOW_VALUE"),
                    RecommendationConfidence.HIGH,
                    "RTP is recommended for immediate low-value domestic payment.",
                    List.of(
                            RecommendationMatchedFactor.DOMESTIC_USD,
                            RecommendationMatchedFactor.IMMEDIATE_URGENCY,
                            RecommendationMatchedFactor.LOW_VALUE),
                    List.of(),
                    List.of(alternative(PaymentRail.ACH, PaymentArrangement.BATCH_CLEARING_NET_SETTLEMENT, intent.clientSegment(), "LOW_COST_NON_URGENT")),
                    List.of(rtpTradeoff(RecommendationIntentFit.HIGH), achTradeoff()),
                    guidance("POST", "/v1/domestic-payments", List.of("payments:create"), "application/pain.001+xml"));
        }

        return supported(
                intent,
                new RecommendationOption(
                        PaymentRail.ACH,
                        PaymentArrangement.BATCH_CLEARING_NET_SETTLEMENT,
                        intent.clientSegment(),
                        "LOW_COST_STANDARD_PAYMENT"),
                RecommendationConfidence.MEDIUM,
                "ACH is recommended for a standard domestic payment where immediate finality is not required.",
                List.of(RecommendationMatchedFactor.DOMESTIC_USD),
                List.of(),
                List.of(alternative(PaymentRail.RTP, PaymentArrangement.DOMESTIC_REAL_TIME_CLEARING, intent.clientSegment(), "IMMEDIATE_ALTERNATIVE")),
                List.of(achTradeoff(), rtpTradeoff(RecommendationIntentFit.MEDIUM)),
                guidance("POST", "/v1/ach-batches", List.of("ach-batches:create"), "application/json"));
    }

    private PaymentRailRecommendation unsupported(
            RecommendationIntent intent,
            String summary,
            RecommendationWarning warning) {
        return PaymentRailRecommendation.unsupported(
                id(),
                summary,
                List.of(warning),
                intent.correlationId());
    }

    private PaymentRailRecommendation supported(
            RecommendationIntent intent,
            RecommendationOption option,
            RecommendationConfidence confidence,
            String summary,
            List<RecommendationMatchedFactor> factors,
            List<RecommendationWarning> warnings,
            List<RecommendationOption> alternatives,
            List<RecommendationTradeoff> tradeoffs,
            RecommendationNextApiGuidance guidance) {
        return new PaymentRailRecommendation(
                id(),
                RecommendationStatus.RECOMMENDED,
                Optional.of(option),
                confidence,
                summary,
                factors,
                warnings,
                alternatives,
                tradeoffs,
                Optional.of(guidance),
                intent.correlationId());
    }

    private boolean isBatchSignal(RecommendationIntent intent) {
        return intent.paymentCount() > 1
                || intent.batchPreferred()
                || intent.costSensitivity() == RecommendationCostSensitivity.HIGH;
    }

    private boolean isHighValue(RecommendationIntent intent) {
        return intent.amountSummary().totalAmount().compareTo(SIMULATOR_HIGH_VALUE_THRESHOLD) >= 0;
    }

    private boolean hasHighValueSingleAmount(RecommendationIntent intent) {
        return intent.amountSummary().maxSingleAmount()
                .map(value -> value.compareTo(SIMULATOR_HIGH_VALUE_THRESHOLD) >= 0)
                .orElse(false);
    }

    private List<RecommendationMatchedFactor> batchFactors(RecommendationIntent intent) {
        var factors = new ArrayList<RecommendationMatchedFactor>();
        factors.add(RecommendationMatchedFactor.DOMESTIC_USD);
        if (intent.paymentCount() > 1) {
            factors.add(RecommendationMatchedFactor.MULTIPLE_PAYMENTS);
        }
        if (intent.batchPreferred()) {
            factors.add(RecommendationMatchedFactor.BATCH_PREFERRED);
        }
        if (intent.costSensitivity() == RecommendationCostSensitivity.HIGH) {
            factors.add(RecommendationMatchedFactor.COST_SENSITIVE);
        }
        return List.copyOf(factors);
    }

    private RecommendationOption alternative(
            PaymentRail rail,
            PaymentArrangement arrangement,
            RecommendationClientSegment clientSegment,
            String reasonCode) {
        return new RecommendationOption(rail, arrangement, clientSegment, reasonCode);
    }

    private RecommendationNextApiGuidance guidance(
            String method,
            String endpoint,
            List<String> requiredScopes,
            String payloadFormat) {
        return new RecommendationNextApiGuidance(
                method,
                endpoint,
                requiredScopes,
                List.of("Authorization", "X-Correlation-ID"),
                payloadFormat);
    }

    private RecommendationTradeoff achTradeoff() {
        return new RecommendationTradeoff(
                PaymentRail.ACH,
                PaymentArrangement.BATCH_CLEARING_NET_SETTLEMENT,
                RecommendationSpeed.LOW,
                RecommendationCost.LOW,
                RecommendationFinality.DEFERRED,
                RecommendationIntentFit.HIGH,
                "Batch clearing fits multiple non-urgent or cost-sensitive payments.",
                "ACH is efficient for batch payments where immediate finality is not required.");
    }

    private RecommendationTradeoff rtgsTradeoff(RecommendationIntentFit fit) {
        return new RecommendationTradeoff(
                PaymentRail.RTGS,
                PaymentArrangement.DOMESTIC_INTERBANK_GROSS_SETTLEMENT,
                RecommendationSpeed.HIGH,
                RecommendationCost.HIGH,
                RecommendationFinality.IMMEDIATE_FINAL,
                fit,
                "Gross settlement fits high-value or finality-driven intent.",
                "RTGS provides final settlement for high-value transfers.");
    }

    private RecommendationTradeoff rtpTradeoff(RecommendationIntentFit fit) {
        return new RecommendationTradeoff(
                PaymentRail.RTP,
                PaymentArrangement.DOMESTIC_REAL_TIME_CLEARING,
                RecommendationSpeed.HIGH,
                RecommendationCost.MEDIUM,
                RecommendationFinality.REAL_TIME,
                fit,
                "Real-time clearing fits immediate low-value payment intent.",
                "RTP fits immediate low-value domestic payments.");
    }

    private RecommendationTradeoff correspondentTradeoff() {
        return new RecommendationTradeoff(
                PaymentRail.FI_CORRESPONDENT,
                PaymentArrangement.CORRESPONDENT_ACCOUNT_PATH,
                RecommendationSpeed.MEDIUM,
                RecommendationCost.MEDIUM,
                RecommendationFinality.CORRESPONDENT_DEPENDENT,
                RecommendationIntentFit.HIGH,
                "Correspondent account path fits FI intents that prefer nostro, vostro, or loro arrangements.",
                "FI correspondent flow models correspondent-account-path settlement context.");
    }

    private PaymentRailRecommendationId id() {
        return new PaymentRailRecommendationId(UUID.randomUUID());
    }
}
