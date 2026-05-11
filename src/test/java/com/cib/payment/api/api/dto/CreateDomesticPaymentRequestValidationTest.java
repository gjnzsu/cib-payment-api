package com.cib.payment.api.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CreateDomesticPaymentRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void validRequestPassesBeanValidation() {
        assertThat(validate(validRequest())).isEmpty();
    }

    @Test
    void missingDebtorAccountFailsValidation() {
        var request = new CreateDomesticPaymentRequest(
                null,
                validAccount(),
                validAmount(),
                "INV-2026-0001",
                null,
                null);

        assertThat(validate(request)).anyMatch(violation -> violation.getPropertyPath().toString().equals("debtorAccount"));
    }

    @Test
    void missingAccountNumberFailsValidation() {
        var request = new CreateDomesticPaymentRequest(
                new AccountReferenceRequest("CIBBMYKL", " ", "Acme Treasury"),
                validAccount(),
                validAmount(),
                "INV-2026-0001",
                null,
                null);

        assertThat(validate(request))
                .anyMatch(violation -> violation.getPropertyPath().toString().equals("debtorAccount.accountNumber"));
    }

    @Test
    void amountWithMoreThanTwoDecimalsFailsValidation() {
        var request = new CreateDomesticPaymentRequest(
                validAccount(),
                validAccount(),
                new MoneyRequest("MYR", "10.999"),
                "INV-2026-0001",
                null,
                null);

        assertThat(validate(request)).anyMatch(violation -> violation.getPropertyPath().toString().equals("amount.value"));
    }

    @Test
    void negativeAmountFailsValidation() {
        var request = new CreateDomesticPaymentRequest(
                validAccount(),
                validAccount(),
                new MoneyRequest("MYR", "-10.00"),
                "INV-2026-0001",
                null,
                null);

        assertThat(validate(request)).anyMatch(violation -> violation.getPropertyPath().toString().equals("amount.value"));
    }

    @Test
    void zeroAmountFailsValidation() {
        var request = new CreateDomesticPaymentRequest(
                validAccount(),
                validAccount(),
                new MoneyRequest("MYR", "0.00"),
                "INV-2026-0001",
                null,
                null);

        assertThat(validate(request)).anyMatch(violation -> violation.getPropertyPath().toString().equals("amount.value"));
    }

    @Test
    void unknownTopLevelFieldFailsDeserialization() {
        assertThatThrownBy(() -> objectMapper.readValue("""
                {
                  "debtorAccount": {
                    "bankCode": "CIBBMYKL",
                    "accountNumber": "1234567890",
                    "accountName": "Acme Treasury"
                  },
                  "creditorAccount": {
                    "bankCode": "PAYBMYKL",
                    "accountNumber": "9876543210",
                    "accountName": "Supplier Sdn Bhd"
                  },
                  "amount": {
                    "currency": "MYR",
                    "value": "1250.50"
                  },
                  "paymentReference": "INV-2026-0001",
                  "unsupported": "value"
                }
                """, CreateDomesticPaymentRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    private Set<jakarta.validation.ConstraintViolation<CreateDomesticPaymentRequest>> validate(
            CreateDomesticPaymentRequest request) {
        return validator.validate(request);
    }

    private CreateDomesticPaymentRequest validRequest() {
        return new CreateDomesticPaymentRequest(
                validAccount(),
                validAccount(),
                validAmount(),
                "INV-2026-0001",
                "Invoice payment",
                null);
    }

    private AccountReferenceRequest validAccount() {
        return new AccountReferenceRequest("CIBBMYKL", "1234567890", "Acme Treasury");
    }

    private MoneyRequest validAmount() {
        return new MoneyRequest("MYR", "1250.50");
    }
}
