package com.cib.payment.api.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CreateRtgsPaymentRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void validCorporateRequestPassesBeanValidation() {
        assertThat(validate(validCorporateRequest())).isEmpty();
    }

    @Test
    void validFiRequestPassesBeanValidation() {
        assertThat(validate(validFiRequest())).isEmpty();
    }

    @Test
    void missingCommonFieldsFailBeanValidation() {
        var request = new CreateRtgsPaymentRequest(null, " ", null, null, null, null, null, null, " ", " ");

        assertThat(paths(validate(request)))
                .contains(
                        "paymentReference",
                        "clientSegment",
                        "amount",
                        "requestedSettlementDate",
                        "settlementPriority",
                        "purpose");
    }

    @Test
    void unsupportedClientSegmentFailsBeanValidation() {
        var request = new CreateRtgsPaymentRequest(
                "RTGS-2026-0001",
                "RETAIL",
                validAccount(),
                validAccount(),
                null,
                null,
                validAmount(),
                LocalDate.of(2026, 6, 5),
                "URGENT",
                "Treasury transfer");

        assertThat(paths(validate(request))).contains("clientSegment");
    }

    @Test
    void unknownTopLevelFieldFailsDeserialization() {
        assertThatThrownBy(() -> objectMapper.readValue("""
                {
                  "paymentReference": "RTGS-2026-0001",
                  "clientSegment": "CORPORATE",
                  "debtorAccount": {
                    "bankCode": "CITIUS33",
                    "accountNumber": "123456789",
                    "accountName": "Acme Operating"
                  },
                  "creditorAccount": {
                    "bankCode": "BOFAUS3N",
                    "accountNumber": "987654321",
                    "accountName": "Beta Supplier"
                  },
                  "amount": {
                    "currency": "USD",
                    "value": "1250.50"
                  },
                  "requestedSettlementDate": "2026-06-05",
                  "settlementPriority": "URGENT",
                  "purpose": "Treasury transfer",
                  "unsupported": "value"
                }
                """, CreateRtgsPaymentRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void corporateRequiredPartiesAreReportedByHelper() {
        var request = new CreateRtgsPaymentRequest(
                "RTGS-2026-0001",
                "CORPORATE",
                null,
                validAccount(),
                null,
                null,
                validAmount(),
                LocalDate.of(2026, 6, 5),
                "URGENT",
                "Treasury transfer");

        assertThat(request.isCorporateSegment()).isTrue();
        assertThat(request.hasRequiredCorporateParties()).isFalse();
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void fiRequiredAgentsAreReportedByHelper() {
        var request = new CreateRtgsPaymentRequest(
                "RTGS-2026-0002",
                "FI",
                null,
                null,
                " ",
                "IRVTUS3NXXX",
                validAmount(),
                LocalDate.of(2026, 6, 5),
                "NORMAL",
                "FI settlement");

        assertThat(request.isFiSegment()).isTrue();
        assertThat(request.hasRequiredFiAgents()).isFalse();
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void nonUsdAmountIsReportedByHelper() {
        var request = new CreateRtgsPaymentRequest(
                "RTGS-2026-0001",
                "CORPORATE",
                validAccount(),
                validAccount(),
                null,
                null,
                new MoneyRequest("EUR", "1250.50"),
                LocalDate.of(2026, 6, 5),
                "URGENT",
                "Treasury transfer");

        assertThat(request.hasUsdAmount()).isFalse();
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void positiveUsdAmountBelowHighValueThresholdIsAcceptedByDtoValidation() {
        var request = new CreateRtgsPaymentRequest(
                "RTGS-2026-0001",
                "CORPORATE",
                validAccount(),
                validAccount(),
                null,
                null,
                new MoneyRequest("USD", "1.00"),
                LocalDate.of(2026, 6, 5),
                "URGENT",
                "Treasury transfer");

        assertThat(request.hasUsdAmount()).isTrue();
        assertThat(validate(request)).isEmpty();
    }

    @Test
    void rtgsCreationResponseExposesClientSegment() {
        var now = Instant.parse("2026-06-01T00:00:00Z");
        var response = new RtgsPaymentResponse(
                "pay_123",
                "RTGS",
                "CORPORATE",
                "ACCEPTED",
                true,
                now,
                now,
                "corr-123",
                null,
                null);

        assertThat(response.clientSegment()).isEqualTo("CORPORATE");
    }

    private Set<ConstraintViolation<CreateRtgsPaymentRequest>> validate(CreateRtgsPaymentRequest request) {
        return validator.validate(request);
    }

    private Set<String> paths(Set<ConstraintViolation<CreateRtgsPaymentRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    private CreateRtgsPaymentRequest validCorporateRequest() {
        return new CreateRtgsPaymentRequest(
                "RTGS-2026-0001",
                "CORPORATE",
                validAccount(),
                validAccount(),
                null,
                null,
                validAmount(),
                LocalDate.of(2026, 6, 5),
                "URGENT",
                "Treasury transfer");
    }

    private CreateRtgsPaymentRequest validFiRequest() {
        return new CreateRtgsPaymentRequest(
                "RTGS-2026-0002",
                "FI",
                null,
                null,
                "CITIUS33XXX",
                "IRVTUS3NXXX",
                validAmount(),
                LocalDate.of(2026, 6, 5),
                "NORMAL",
                "FI settlement");
    }

    private AccountReferenceRequest validAccount() {
        return new AccountReferenceRequest("CITIUS33", "123456789", "Acme Operating");
    }

    private MoneyRequest validAmount() {
        return new MoneyRequest("USD", "1250.50");
    }
}
