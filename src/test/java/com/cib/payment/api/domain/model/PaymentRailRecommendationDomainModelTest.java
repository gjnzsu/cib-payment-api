package com.cib.payment.api.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentRailRecommendationDomainModelTest {
    @Test
    void recommendationEnumsContainSupportedMvpValues() {
        assertThat(Arrays.stream(RecommendationStatus.values()).map(Enum::name))
                .containsExactly("RECOMMENDED", "UNSUPPORTED");
        assertThat(Arrays.stream(PaymentRail.values()).map(Enum::name))
                .containsExactly("RTP", "ACH", "RTGS", "FI_CORRESPONDENT");
        assertThat(Arrays.stream(PaymentArrangement.values()).map(Enum::name))
                .containsExactly(
                        "DOMESTIC_REAL_TIME_CLEARING",
                        "BATCH_CLEARING_NET_SETTLEMENT",
                        "DOMESTIC_INTERBANK_GROSS_SETTLEMENT",
                        "CORRESPONDENT_ACCOUNT_PATH");
        assertThat(Arrays.stream(RecommendationConfidence.values()).map(Enum::name))
                .containsExactly("HIGH", "MEDIUM", "LOW");
        assertThat(Arrays.stream(RecommendationIntentFit.values()).map(Enum::name))
                .containsExactly("HIGH", "MEDIUM", "LOW");
        assertThat(Arrays.stream(RecommendationCost.values()).map(Enum::name))
                .containsExactly("LOW", "MEDIUM", "HIGH");
        assertThat(Arrays.stream(RecommendationSpeed.values()).map(Enum::name))
                .containsExactly("LOW", "MEDIUM", "HIGH");
        assertThat(Arrays.stream(RecommendationFinality.values()).map(Enum::name))
                .containsExactly("DEFERRED", "REAL_TIME", "IMMEDIATE_FINAL", "CORRESPONDENT_DEPENDENT");
    }

    @Test
    void recommendationRecordKeepsRailArrangementExplanationGuidanceAndCorrelation() {
        var recommendationId = new PaymentRailRecommendationId(
                UUID.fromString("550e8400-e29b-41d4-a716-446655441001"));
        var recommendedOption = new RecommendationOption(
                PaymentRail.RTGS,
                PaymentArrangement.DOMESTIC_INTERBANK_GROSS_SETTLEMENT,
                RecommendationClientSegment.CORPORATE,
                "HIGH_VALUE_FINALITY");
        var warning = new RecommendationWarning(
                "BATCH_FINALITY_CONFLICT",
                "Batch intent includes finality signal; review RTGS for high-value entries.");
        var tradeoff = new RecommendationTradeoff(
                PaymentRail.RTGS,
                PaymentArrangement.DOMESTIC_INTERBANK_GROSS_SETTLEMENT,
                RecommendationSpeed.HIGH,
                RecommendationCost.HIGH,
                RecommendationFinality.IMMEDIATE_FINAL,
                RecommendationIntentFit.HIGH,
                "Gross settlement fits high-value finality intent.",
                "RTGS provides final settlement for high-value transfers.");
        var guidance = new RecommendationNextApiGuidance(
                "POST",
                "/v1/rtgs-payments",
                List.of("rtgs-payments:create"),
                List.of("Authorization", "Idempotency-Key", "X-Correlation-ID"),
                "application/json");

        var recommendation = new PaymentRailRecommendation(
                recommendationId,
                RecommendationStatus.RECOMMENDED,
                Optional.of(recommendedOption),
                RecommendationConfidence.HIGH,
                "RTGS is recommended because the payment is high value and requires finality.",
                List.of(RecommendationMatchedFactor.HIGH_VALUE, RecommendationMatchedFactor.REQUIRES_FINALITY),
                List.of(warning),
                List.of(new RecommendationOption(
                        PaymentRail.ACH,
                        PaymentArrangement.BATCH_CLEARING_NET_SETTLEMENT,
                        RecommendationClientSegment.CORPORATE,
                        "BATCH_EFFICIENCY")),
                List.of(tradeoff),
                Optional.of(guidance),
                new CorrelationId("corr-rec-001"));

        assertThat(recommendation.recommendationId()).isEqualTo(recommendationId);
        assertThat(recommendation.recommendationStatus()).isEqualTo(RecommendationStatus.RECOMMENDED);
        assertThat(recommendation.recommendedOption()).contains(recommendedOption);
        assertThat(recommendation.confidenceLevel()).isEqualTo(RecommendationConfidence.HIGH);
        assertThat(recommendation.matchedFactors())
                .containsExactly(RecommendationMatchedFactor.HIGH_VALUE, RecommendationMatchedFactor.REQUIRES_FINALITY);
        assertThat(recommendation.warnings()).containsExactly(warning);
        assertThat(recommendation.tradeoffs()).containsExactly(tradeoff);
        assertThat(recommendation.nextApiGuidance()).contains(guidance);
        assertThat(recommendation.correlationId()).isEqualTo(new CorrelationId("corr-rec-001"));
    }

    @Test
    void unsupportedRecommendationHasNoRecommendedOptionOrGuidance() {
        var unsupported = PaymentRailRecommendation.unsupported(
                new PaymentRailRecommendationId(UUID.fromString("550e8400-e29b-41d4-a716-446655441002")),
                "Cross-border payment intents are outside the simulator recommendation scope.",
                List.of(new RecommendationWarning("CROSS_BORDER_NOT_SUPPORTED", "Only domestic US intents are supported.")),
                new CorrelationId("corr-rec-unsupported"));

        assertThat(unsupported.recommendationStatus()).isEqualTo(RecommendationStatus.UNSUPPORTED);
        assertThat(unsupported.recommendedOption()).isEmpty();
        assertThat(unsupported.nextApiGuidance()).isEmpty();
        assertThat(unsupported.confidenceLevel()).isEqualTo(RecommendationConfidence.LOW);
        assertThat(unsupported.warnings()).extracting(RecommendationWarning::code)
                .containsExactly("CROSS_BORDER_NOT_SUPPORTED");
    }

    @Test
    void amountSummaryRequiresCurrencyAndPositiveTotalAmount() {
        var amountSummary = new RecommendationAmountSummary(
                "USD",
                "125000.00",
                Optional.of("1200.00"));

        assertThat(amountSummary.currency()).isEqualTo("USD");
        assertThat(amountSummary.totalAmount()).isEqualByComparingTo("125000.00");
        assertThat(amountSummary.maxSingleAmount()).contains(new java.math.BigDecimal("1200.00"));

        assertThrows(IllegalArgumentException.class, () -> new RecommendationAmountSummary("", "1.00", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new RecommendationAmountSummary("USD", "0.00", Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new RecommendationAmountSummary("USD", "1.00", Optional.of("0.00")));
    }

    @Test
    void debtorAccountProfileDefaultsToOneAndRejectsInvalidCount() {
        assertThat(RecommendationDebtorAccountProfile.defaultProfile().count()).isEqualTo(1);
        assertThat(new RecommendationDebtorAccountProfile(2).count()).isEqualTo(2);
        assertThrows(IllegalArgumentException.class, () -> new RecommendationDebtorAccountProfile(0));
    }
}
