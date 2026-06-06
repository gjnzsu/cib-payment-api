package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.PaymentArrangement;
import com.cib.payment.api.domain.model.PaymentRail;
import com.cib.payment.api.domain.model.RecommendationAmountSummary;
import com.cib.payment.api.domain.model.RecommendationClientSegment;
import com.cib.payment.api.domain.model.RecommendationConfidence;
import com.cib.payment.api.domain.model.RecommendationCostSensitivity;
import com.cib.payment.api.domain.model.RecommendationDebtorAccountProfile;
import com.cib.payment.api.domain.model.RecommendationIntent;
import com.cib.payment.api.domain.model.RecommendationMatchedFactor;
import com.cib.payment.api.domain.model.RecommendationStatus;
import com.cib.payment.api.domain.model.RecommendationUrgency;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PaymentRailRecommendationRuleEngineTest {
    private final PaymentRailRecommendationRuleEngine engine = new PaymentRailRecommendationRuleEngine();

    @Test
    void returnsUnsupportedForCrossBorderIntent() {
        var recommendation = engine.recommend(intentBuilder()
                .debtorCountry("US")
                .creditorCountry("GB")
                .build());

        assertThat(recommendation.recommendationStatus()).isEqualTo(RecommendationStatus.UNSUPPORTED);
        assertThat(recommendation.recommendedOption()).isEmpty();
        assertThat(recommendation.nextApiGuidance()).isEmpty();
        assertThat(recommendation.warnings()).extracting(warning -> warning.code())
                .containsExactly("CROSS_BORDER_NOT_SUPPORTED");
    }

    @Test
    void returnsUnsupportedForNonUsdIntent() {
        var recommendation = engine.recommend(intentBuilder()
                .amountSummary(new RecommendationAmountSummary("EUR", "1000.00", Optional.empty()))
                .build());

        assertThat(recommendation.recommendationStatus()).isEqualTo(RecommendationStatus.UNSUPPORTED);
        assertThat(recommendation.warnings()).extracting(warning -> warning.code())
                .containsExactly("NON_USD_NOT_SUPPORTED");
    }

    @Test
    void recommendsFiCorrespondentWhenFiClientPrefersCorrespondentAccountPath() {
        var recommendation = engine.recommend(intentBuilder()
                .clientSegment(RecommendationClientSegment.FI)
                .fiToFi(true)
                .arrangementPreference(PaymentArrangement.CORRESPONDENT_ACCOUNT_PATH)
                .build());

        assertThat(recommendation.recommendedOption()).hasValueSatisfying(option -> {
            assertThat(option.rail()).isEqualTo(PaymentRail.FI_CORRESPONDENT);
            assertThat(option.arrangement()).isEqualTo(PaymentArrangement.CORRESPONDENT_ACCOUNT_PATH);
        });
        assertThat(recommendation.matchedFactors())
                .contains(RecommendationMatchedFactor.FI_CLIENT, RecommendationMatchedFactor.CORRESPONDENT_ACCOUNT_PATH);
    }

    @Test
    void recommendsAchForMultiplePaymentBatchIntent() {
        var recommendation = engine.recommend(intentBuilder()
                .paymentCount(250)
                .batchPreferred(true)
                .urgency(RecommendationUrgency.STANDARD)
                .costSensitivity(RecommendationCostSensitivity.HIGH)
                .amountSummary(new RecommendationAmountSummary("USD", "125000.00", Optional.of("1200.00")))
                .build());

        assertThat(recommendation.recommendedOption()).hasValueSatisfying(option -> {
            assertThat(option.rail()).isEqualTo(PaymentRail.ACH);
            assertThat(option.arrangement()).isEqualTo(PaymentArrangement.BATCH_CLEARING_NET_SETTLEMENT);
        });
        assertThat(recommendation.confidenceLevel()).isEqualTo(RecommendationConfidence.HIGH);
        assertThat(recommendation.matchedFactors())
                .contains(RecommendationMatchedFactor.MULTIPLE_PAYMENTS, RecommendationMatchedFactor.BATCH_PREFERRED);
    }

    @Test
    void recommendsAchAndWarnsWhenBatchOmitsMaximumSingleAmount() {
        var recommendation = engine.recommend(intentBuilder()
                .paymentCount(100)
                .amountSummary(new RecommendationAmountSummary("USD", "500000.00", Optional.empty()))
                .build());

        assertThat(recommendation.recommendedOption()).hasValueSatisfying(option ->
                assertThat(option.rail()).isEqualTo(PaymentRail.ACH));
        assertThat(recommendation.warnings()).extracting(warning -> warning.code())
                .contains("MAX_SINGLE_AMOUNT_NOT_PROVIDED");
    }

    @Test
    void recommendsAchWithRtgsAlternativeWhenBatchContainsHighValueSingleAmount() {
        var recommendation = engine.recommend(intentBuilder()
                .paymentCount(20)
                .requiresFinality(true)
                .amountSummary(new RecommendationAmountSummary("USD", "500000.00", Optional.of("150000.00")))
                .build());

        assertThat(recommendation.recommendedOption()).hasValueSatisfying(option ->
                assertThat(option.rail()).isEqualTo(PaymentRail.ACH));
        assertThat(recommendation.warnings()).extracting(warning -> warning.code())
                .contains("BATCH_HIGH_VALUE_ENTRY_REVIEW");
        assertThat(recommendation.alternatives()).anySatisfy(option ->
                assertThat(option.rail()).isEqualTo(PaymentRail.RTGS));
    }

    @Test
    void recommendsRtgsForHighValueFinalitySinglePayment() {
        var recommendation = engine.recommend(intentBuilder()
                .requiresFinality(true)
                .urgency(RecommendationUrgency.SAME_DAY)
                .amountSummary(new RecommendationAmountSummary("USD", "250000.00", Optional.of("250000.00")))
                .build());

        assertThat(recommendation.recommendedOption()).hasValueSatisfying(option -> {
            assertThat(option.rail()).isEqualTo(PaymentRail.RTGS);
            assertThat(option.arrangement()).isEqualTo(PaymentArrangement.DOMESTIC_INTERBANK_GROSS_SETTLEMENT);
        });
        assertThat(recommendation.matchedFactors())
                .contains(RecommendationMatchedFactor.HIGH_VALUE, RecommendationMatchedFactor.REQUIRES_FINALITY);
    }

    @Test
    void recommendsRtpForImmediateLowValueSinglePayment() {
        var recommendation = engine.recommend(intentBuilder()
                .urgency(RecommendationUrgency.IMMEDIATE)
                .amountSummary(new RecommendationAmountSummary("USD", "250.00", Optional.of("250.00")))
                .build());

        assertThat(recommendation.recommendedOption()).hasValueSatisfying(option -> {
            assertThat(option.rail()).isEqualTo(PaymentRail.RTP);
            assertThat(option.arrangement()).isEqualTo(PaymentArrangement.DOMESTIC_REAL_TIME_CLEARING);
        });
        assertThat(recommendation.nextApiGuidance()).hasValueSatisfying(guidance ->
                assertThat(guidance.endpoint()).isEqualTo("/v1/domestic-payments"));
    }

    private RecommendationIntent.Builder intentBuilder() {
        return RecommendationIntent.builder()
                .clientSegment(RecommendationClientSegment.CORPORATE)
                .paymentPurpose("SUPPLIER_PAYMENT")
                .paymentCount(1)
                .amountSummary(new RecommendationAmountSummary("USD", "1000.00", Optional.of("1000.00")))
                .debtorCountry("US")
                .creditorCountry("US")
                .urgency(RecommendationUrgency.SAME_DAY)
                .creditorType("CORPORATE")
                .requiresFinality(false)
                .batchPreferred(false)
                .costSensitivity(RecommendationCostSensitivity.MEDIUM)
                .fiToFi(false)
                .debtorAccountProfile(RecommendationDebtorAccountProfile.defaultProfile())
                .correlationId(new CorrelationId("corr-rec-rule"));
    }
}
