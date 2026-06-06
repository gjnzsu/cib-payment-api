package com.cib.payment.api.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CreatePaymentRailRecommendationRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void validSinglePaymentIntentPassesBeanValidation() {
        assertThat(validate(validSinglePaymentIntent())).isEmpty();
    }

    @Test
    void validBatchIntentPassesBeanValidationWithoutAverageAmount() {
        var request = new CreatePaymentRailRecommendationRequest(
                "CORPORATE",
                "PAYROLL",
                250,
                new RecommendationAmountSummaryRequest("USD", "125000.00", null),
                "US",
                "US",
                "STANDARD",
                "EMPLOYEE",
                false,
                true,
                "HIGH",
                false,
                null,
                null);

        assertThat(validate(request)).isEmpty();
        assertThat(request.debtorAccountCount()).isEqualTo(1);
    }

    @Test
    void missingRequiredFieldsFailBeanValidation() {
        var request = new CreatePaymentRailRecommendationRequest(
                null,
                null,
                0,
                null,
                " ",
                null,
                " ",
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertThat(paths(validate(request)))
                .contains(
                        "clientSegment",
                        "paymentCount",
                        "amountSummary",
                        "debtorCountry",
                        "creditorCountry",
                        "urgency");
    }

    @Test
    void invalidEnumValuesFailBeanValidation() {
        var request = new CreatePaymentRailRecommendationRequest(
                "RETAIL",
                "SUPPLIER_PAYMENT",
                1,
                new RecommendationAmountSummaryRequest("USD", "250.00", null),
                "US",
                "US",
                "NOW",
                "CORPORATE",
                false,
                false,
                "FREE",
                false,
                "UNKNOWN_PATH",
                null);

        assertThat(paths(validate(request)))
                .contains("clientSegment", "urgency", "costSensitivity", "arrangementPreference");
    }

    @Test
    void invalidAmountSummaryFailsBeanValidation() {
        var request = new CreatePaymentRailRecommendationRequest(
                "CORPORATE",
                "SUPPLIER_PAYMENT",
                1,
                new RecommendationAmountSummaryRequest(" ", "0.00", "0.00"),
                "US",
                "US",
                "IMMEDIATE",
                "CORPORATE",
                false,
                false,
                "MEDIUM",
                false,
                null,
                null);

        assertThat(paths(validate(request)))
                .contains(
                        "amountSummary.currency",
                        "amountSummary.totalAmount",
                        "amountSummary.maxSingleAmount");
    }

    @Test
    void unknownTopLevelFieldFailsDeserialization() {
        assertThatThrownBy(() -> objectMapper.readValue("""
                {
                  "clientSegment": "CORPORATE",
                  "paymentPurpose": "SUPPLIER_PAYMENT",
                  "paymentCount": 1,
                  "amountSummary": {
                    "currency": "USD",
                    "totalAmount": "250.00"
                  },
                  "debtorCountry": "US",
                  "creditorCountry": "US",
                  "urgency": "IMMEDIATE",
                  "unsupported": "value"
                }
                """, CreatePaymentRailRecommendationRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void responseDtosExposeRecommendationExplanationFields() {
        var response = new PaymentRailRecommendationResponse(
                "rec_123",
                "RECOMMENDED",
                new RecommendationOptionResponse(
                        "RTP",
                        "DOMESTIC_REAL_TIME_CLEARING",
                        "CORPORATE",
                        "IMMEDIATE_LOW_VALUE"),
                "HIGH",
                "RTP is recommended for immediate low-value domestic payment.",
                List.of("DOMESTIC_USD", "IMMEDIATE_URGENCY", "LOW_VALUE"),
                List.of(),
                List.of(),
                List.of(new RecommendationTradeoffResponse(
                        "RTP",
                        "DOMESTIC_REAL_TIME_CLEARING",
                        "HIGH",
                        "MEDIUM",
                        "REAL_TIME",
                        "HIGH",
                        "Real-time clearing fits immediate payment intent.",
                        "RTP fits immediate low-value domestic payments.")),
                new RecommendationNextApiGuidanceResponse(
                        "POST",
                        "/v1/domestic-payments",
                        List.of("payments:create"),
                        List.of("Authorization", "Idempotency-Key", "X-Correlation-ID"),
                        "application/pain.001+xml"),
                "corr-rec-123");

        assertThat(response.recommendationStatus()).isEqualTo("RECOMMENDED");
        assertThat(response.recommendedOption().rail()).isEqualTo("RTP");
        assertThat(response.tradeoffs()).extracting(RecommendationTradeoffResponse::intentFit)
                .containsExactly("HIGH");
        assertThat(response.nextApiGuidance().endpoint()).isEqualTo("/v1/domestic-payments");
    }

    private Set<ConstraintViolation<CreatePaymentRailRecommendationRequest>> validate(
            CreatePaymentRailRecommendationRequest request) {
        return validator.validate(request);
    }

    private Set<String> paths(Set<ConstraintViolation<CreatePaymentRailRecommendationRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    private CreatePaymentRailRecommendationRequest validSinglePaymentIntent() {
        return new CreatePaymentRailRecommendationRequest(
                "CORPORATE",
                "SUPPLIER_PAYMENT",
                1,
                new RecommendationAmountSummaryRequest("USD", "250.00", "250.00"),
                "US",
                "US",
                "IMMEDIATE",
                "CORPORATE",
                false,
                false,
                "MEDIUM",
                false,
                null,
                new RecommendationDebtorAccountProfileRequest(1));
    }
}
