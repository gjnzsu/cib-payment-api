package com.cib.payment.api.application.service;

import com.cib.payment.api.domain.model.*;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class PaymentScenarioAdvisorCatalogService {
    private static final CorrelationId CATALOG_CORRELATION_ID = new CorrelationId("advisor-catalog");

    private final List<AdvisorScenario> scenarios = List.of(
            urgentSupplierPayment(),
            vendorBatchPayment(),
            highValueTreasuryTransfer(),
            fiCorrespondentSettlement());

    public List<AdvisorScenario> listScenarios() {
        return scenarios;
    }

    public Optional<AdvisorScenario> getScenario(String scenarioId) {
        return scenarios.stream()
                .filter(scenario -> scenario.scenarioId().value().equals(scenarioId))
                .findFirst();
    }

    private AdvisorScenario urgentSupplierPayment() {
        return scenario(
                "urgent-supplier-payment",
                "Urgent supplier payment",
                "Immediate low-value domestic supplier invoice payment.",
                RecommendationIntent.builder()
                        .clientSegment(RecommendationClientSegment.CORPORATE)
                        .paymentPurpose("SUPPLIER_PAYMENT")
                        .paymentCount(1)
                        .amountSummary(new RecommendationAmountSummary("USD", "1250.50", Optional.of("1250.50")))
                        .debtorCountry("US")
                        .creditorCountry("US")
                        .urgency(RecommendationUrgency.IMMEDIATE)
                        .creditorType("CORPORATE")
                        .requiresFinality(false)
                        .batchPreferred(false)
                        .costSensitivity(RecommendationCostSensitivity.MEDIUM)
                        .fiToFi(false)
                        .correlationId(CATALOG_CORRELATION_ID)
                        .build(),
                recommendation(
                        PaymentRail.RTP,
                        PaymentArrangement.DOMESTIC_REAL_TIME_CLEARING,
                        RecommendationClientSegment.CORPORATE,
                        "IMMEDIATE_LOW_VALUE",
                        "RTP fits an immediate low-value domestic supplier payment.",
                        List.of("DOMESTIC_USD", "IMMEDIATE_URGENCY", "LOW_VALUE"),
                        List.of("Fast real-time clearing with medium simulator cost profile.")),
                plan(
                        "/v1/domestic-payments",
                        List.of("payments:create", "payments:read"),
                        "application/pain.001+xml",
                        "success",
                        "/v1/domestic-payments/{paymentId}",
                        "ACSC"),
                report(
                        "VALIDATED",
                        "ACSC",
                        "The supplier payment scenario validates against the domestic RTP simulator.",
                        "Use payments:create for initiation and payments:read for status checks."));
    }

    private AdvisorScenario vendorBatchPayment() {
        return scenario(
                "vendor-batch-payment",
                "Vendor batch payment",
                "Multiple non-urgent vendor payments where batch efficiency matters.",
                RecommendationIntent.builder()
                        .clientSegment(RecommendationClientSegment.CORPORATE)
                        .paymentPurpose("VENDOR_BATCH")
                        .paymentCount(250)
                        .amountSummary(new RecommendationAmountSummary("USD", "125000.00", Optional.of("1200.00")))
                        .debtorCountry("US")
                        .creditorCountry("US")
                        .urgency(RecommendationUrgency.STANDARD)
                        .creditorType("CORPORATE")
                        .requiresFinality(false)
                        .batchPreferred(true)
                        .costSensitivity(RecommendationCostSensitivity.HIGH)
                        .fiToFi(false)
                        .correlationId(CATALOG_CORRELATION_ID)
                        .build(),
                recommendation(
                        PaymentRail.ACH,
                        PaymentArrangement.BATCH_CLEARING_NET_SETTLEMENT,
                        RecommendationClientSegment.CORPORATE,
                        "BATCH_EFFICIENCY",
                        "ACH fits multiple non-urgent or cost-sensitive vendor payments.",
                        List.of("DOMESTIC_USD", "MULTIPLE_PAYMENTS", "BATCH_PREFERRED", "COST_SENSITIVE"),
                        List.of("Efficient for batches where immediate finality is not required.")),
                plan(
                        "/v1/ach-batches",
                        List.of("ach-batches:create", "ach-batches:read"),
                        "application/json",
                        "ach_direct_credit_settled",
                        "/v1/ach-batches/{batchId}",
                        "SETTLED"),
                report(
                        "VALIDATED",
                        "SETTLED",
                        "The vendor batch scenario validates against the ACH Direct Credit simulator.",
                        "Use ach-batches:create for batch submission and ach-batches:read for status checks."));
    }

    private AdvisorScenario highValueTreasuryTransfer() {
        return scenario(
                "high-value-treasury-transfer",
                "High-value treasury transfer",
                "Single high-value domestic transfer where final settlement is important.",
                RecommendationIntent.builder()
                        .clientSegment(RecommendationClientSegment.CORPORATE)
                        .paymentPurpose("TREASURY_TRANSFER")
                        .paymentCount(1)
                        .amountSummary(new RecommendationAmountSummary("USD", "250000.00", Optional.of("250000.00")))
                        .debtorCountry("US")
                        .creditorCountry("US")
                        .urgency(RecommendationUrgency.SAME_DAY)
                        .creditorType("CORPORATE")
                        .requiresFinality(true)
                        .batchPreferred(false)
                        .costSensitivity(RecommendationCostSensitivity.MEDIUM)
                        .fiToFi(false)
                        .correlationId(CATALOG_CORRELATION_ID)
                        .build(),
                recommendation(
                        PaymentRail.RTGS,
                        PaymentArrangement.DOMESTIC_INTERBANK_GROSS_SETTLEMENT,
                        RecommendationClientSegment.CORPORATE,
                        "HIGH_VALUE_FINALITY",
                        "RTGS fits high-value or finality-driven domestic transfers.",
                        List.of("DOMESTIC_USD", "HIGH_VALUE", "REQUIRES_FINALITY"),
                        List.of("Gross settlement is higher cost but better aligned to finality.")),
                plan(
                        "/v1/rtgs-payments",
                        List.of("rtgs-payments:create", "rtgs-payments:read"),
                        "application/json",
                        "rtgs_settled",
                        "/v1/rtgs-payments/{paymentId}",
                        "SETTLED"),
                report(
                        "VALIDATED",
                        "SETTLED",
                        "The treasury transfer scenario validates against the RTGS simulator.",
                        "Use rtgs-payments:create for initiation and rtgs-payments:read for status checks."));
    }

    private AdvisorScenario fiCorrespondentSettlement() {
        return scenario(
                "fi-correspondent-settlement",
                "FI correspondent settlement",
                "FI-to-FI payment intent that prefers correspondent account path semantics.",
                RecommendationIntent.builder()
                        .clientSegment(RecommendationClientSegment.FI)
                        .paymentPurpose("FI_CORRESPONDENT_SETTLEMENT")
                        .paymentCount(1)
                        .amountSummary(new RecommendationAmountSummary("USD", "1000000.00", Optional.of("1000000.00")))
                        .debtorCountry("US")
                        .creditorCountry("US")
                        .urgency(RecommendationUrgency.SAME_DAY)
                        .creditorType("FI")
                        .requiresFinality(false)
                        .batchPreferred(false)
                        .costSensitivity(RecommendationCostSensitivity.MEDIUM)
                        .fiToFi(true)
                        .arrangementPreference(PaymentArrangement.CORRESPONDENT_ACCOUNT_PATH)
                        .correlationId(CATALOG_CORRELATION_ID)
                        .build(),
                recommendation(
                        PaymentRail.FI_CORRESPONDENT,
                        PaymentArrangement.CORRESPONDENT_ACCOUNT_PATH,
                        RecommendationClientSegment.FI,
                        "FI_CORRESPONDENT_ACCOUNT_PATH",
                        "FI correspondent arrangement fits FI intents that prefer nostro, vostro, or loro context.",
                        List.of("DOMESTIC_USD", "FI_CLIENT", "CORRESPONDENT_ACCOUNT_PATH"),
                        List.of("Settlement context depends on correspondent-account-path simulation.")),
                plan(
                        "/v1/fi-payments",
                        List.of("fi-payments:create", "fi-payments:read"),
                        "application/pacs.009+xml",
                        "fi_payment_accepted",
                        "/v1/fi-payments/{paymentId}",
                        "ACCEPTED"),
                report(
                        "VALIDATED",
                        "ACCEPTED",
                        "The FI correspondent scenario validates against the FI payment simulator.",
                        "Use fi-payments:create for initiation and fi-payments:read for status checks."));
    }

    private AdvisorScenario scenario(
            String id,
            String label,
            String description,
            RecommendationIntent intent,
            AdvisorRecommendationSummary recommendation,
            AdvisorSimulationPlan plan,
            AdvisorFeedbackReport report) {
        return new AdvisorScenario(
                new AdvisorScenarioId(id),
                label,
                description,
                true,
                true,
                intent,
                recommendation,
                plan,
                report);
    }

    private AdvisorRecommendationSummary recommendation(
            PaymentRail rail,
            PaymentArrangement arrangement,
            RecommendationClientSegment clientSegment,
            String reasonCode,
            String summary,
            List<String> matchedFactors,
            List<String> tradeoffs) {
        return new AdvisorRecommendationSummary(
                rail,
                arrangement,
                clientSegment,
                RecommendationConfidence.HIGH,
                reasonCode,
                summary,
                matchedFactors,
                tradeoffs);
    }

    private AdvisorSimulationPlan plan(
            String endpoint,
            List<String> requiredScopes,
            String payloadFormat,
            String mockScenario,
            String statusEndpointTemplate,
            String expectedStatus) {
        return new AdvisorSimulationPlan(
                "POST",
                endpoint,
                requiredScopes,
                List.of("Authorization", "X-Correlation-ID"),
                true,
                payloadFormat,
                mockScenario,
                statusEndpointTemplate,
                expectedStatus,
                true,
                true);
    }

    private AdvisorFeedbackReport report(
            String validationStatus,
            String expectedOutcome,
            String businessConclusion,
            String nextStep) {
        return new AdvisorFeedbackReport(validationStatus, expectedOutcome, businessConclusion, nextStep);
    }
}
