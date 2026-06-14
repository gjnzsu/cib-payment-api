package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.api.dto.CreatePaymentRailRecommendationRequest;
import com.cib.payment.api.api.dto.PaymentRailRecommendationResponse;
import com.cib.payment.api.application.port.PaymentObservability;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class PaymentRailRecommendationGoldenDatasetTest {
    private static final String FIXTURE =
            "/fixtures/payment-rail-recommendations/golden-dataset.json";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private final CreatePaymentRailRecommendationService service = new CreatePaymentRailRecommendationService(
            new PaymentRailRecommendationRuleEngine(),
            PaymentObservability.noop());

    @ParameterizedTest(name = "{0}")
    @MethodSource("recommendationCases")
    void goldenRecommendationCasesMatchExpectedRailArrangementWarningsAndGuidance(GoldenCase goldenCase) {
        var request = OBJECT_MAPPER.convertValue(goldenCase.request(), CreatePaymentRailRecommendationRequest.class);

        var violations = VALIDATOR.validate(request);
        assertThat(violations).as(goldenCase.scenarioId() + " should be a valid recommendation request").isEmpty();

        var response = service.recommend(request, authorizationContext(goldenCase.scenarioId()));

        assertExpectedRecommendation(goldenCase, response);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("validationCases")
    void goldenValidationCasesAreRejectedBeforeRecommendationEvaluation(GoldenCase goldenCase) {
        var request = OBJECT_MAPPER.convertValue(goldenCase.request(), CreatePaymentRailRecommendationRequest.class);

        assertThat(VALIDATOR.validate(request))
                .as(goldenCase.scenarioId() + " should fail DTO validation")
                .isNotEmpty();
    }

    private static Stream<GoldenCase> recommendationCases() throws Exception {
        return dataset().stream().filter(goldenCase -> "RECOMMENDATION".equals(goldenCase.caseType()));
    }

    private static Stream<GoldenCase> validationCases() throws Exception {
        return dataset().stream().filter(goldenCase -> "VALIDATION".equals(goldenCase.caseType()));
    }

    private static List<GoldenCase> dataset() throws Exception {
        try (InputStream input = PaymentRailRecommendationGoldenDatasetTest.class.getResourceAsStream(FIXTURE)) {
            assertThat(input).as("golden dataset fixture exists").isNotNull();
            return OBJECT_MAPPER.readerForListOf(GoldenCase.class).readValue(input);
        }
    }

    private void assertExpectedRecommendation(GoldenCase goldenCase, PaymentRailRecommendationResponse response) {
        var expected = goldenCase.expected();
        assertThat(response.recommendationStatus()).as(goldenCase.scenarioId()).isEqualTo(expected.recommendationStatus());
        assertThat(response.confidenceLevel()).as(goldenCase.scenarioId()).isEqualTo(expected.confidenceLevel());
        assertThat(response.matchedFactors()).as(goldenCase.scenarioId()).containsAll(expected.requiredMatchedFactors());
        assertThat(response.warnings().stream().map(warning -> warning.code()).toList())
                .as(goldenCase.scenarioId())
                .containsAll(expected.requiredWarnings());

        if ("RECOMMENDED".equals(expected.recommendationStatus())) {
            assertThat(response.recommendedOption()).as(goldenCase.scenarioId()).isNotNull();
            assertThat(response.recommendedOption().rail()).as(goldenCase.scenarioId()).isEqualTo(expected.rail());
            assertThat(response.recommendedOption().arrangement()).as(goldenCase.scenarioId()).isEqualTo(expected.arrangement());
            assertThat(response.nextApiGuidance()).as(goldenCase.scenarioId()).isNotNull();
            assertThat(response.nextApiGuidance().endpoint()).as(goldenCase.scenarioId()).isEqualTo(expected.nextApiEndpoint());
        } else {
            assertThat(response.recommendedOption()).as(goldenCase.scenarioId()).isNull();
            assertThat(response.nextApiGuidance()).as(goldenCase.scenarioId()).isNull();
        }

        assertThat(response.alternatives().stream().map(option -> option.rail()).toList())
                .as(goldenCase.scenarioId())
                .containsAll(expected.requiredAlternativeRails());
    }

    private AuthorizationContext authorizationContext(String scenarioId) {
        return new AuthorizationContext(
                "golden-dataset-client",
                "golden-dataset-client",
                Set.of("payment-rail-recommendations:create"),
                null,
                Map.of(),
                Instant.parse("2026-06-06T00:00:00Z"),
                null,
                new CorrelationId("corr-" + scenarioId));
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record GoldenCase(
            String scenarioId,
            String caseType,
            String purpose,
            Map<String, Object> request,
            Expected expected) {
        @Override
        public String toString() {
            return scenarioId + " - " + purpose;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    record Expected(
            String recommendationStatus,
            String rail,
            String arrangement,
            String confidenceLevel,
            List<String> requiredMatchedFactors,
            List<String> requiredWarnings,
            List<String> requiredAlternativeRails,
            String nextApiEndpoint) {}
}
