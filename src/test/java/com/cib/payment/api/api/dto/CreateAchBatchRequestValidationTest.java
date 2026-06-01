package com.cib.payment.api.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CreateAchBatchRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void validDirectCreditBatchPassesBeanValidation() {
        assertThat(validate(validRequest())).isEmpty();
    }

    @Test
    void emptyEntriesFailValidation() {
        var request = new CreateAchBatchRequest(
                "ACH-2026-0001",
                "Acme Payroll",
                LocalDate.of(2026, 6, 5),
                validAccount(),
                List.of());

        assertThat(validate(request)).anyMatch(violation -> violation.getPropertyPath().toString().equals("entries"));
    }

    @Test
    void nullEntryElementFailsValidation() {
        var request = new CreateAchBatchRequest(
                "ACH-2026-0001",
                "Acme Payroll",
                LocalDate.of(2026, 6, 5),
                validAccount(),
                java.util.Collections.singletonList(null));

        assertThat(paths(validate(request))).contains("entries[0].<list element>");
    }

    @Test
    void missingRequiredBatchFieldsFailValidation() {
        var request = new CreateAchBatchRequest(null, " ", null, null, null);

        assertThat(paths(validate(request)))
                .contains("batchReference", "originatorName", "effectiveEntryDate", "settlementAccount", "entries");
    }

    @Test
    void missingRequiredEntryFieldsFailValidation() {
        var request = new CreateAchBatchRequest(
                "ACH-2026-0001",
                "Acme Payroll",
                LocalDate.of(2026, 6, 5),
                validAccount(),
                List.of(new AchBatchEntryRequest(null, " ", null, null, " ")));

        assertThat(paths(validate(request)))
                .contains(
                        "entries[0].entryReference",
                        "entries[0].receiverName",
                        "entries[0].receiverAccount",
                        "entries[0].amount",
                        "entries[0].purpose");
    }

    @Test
    void nestedAccountAndAmountValidationIsAppliedToEntries() {
        var request = new CreateAchBatchRequest(
                "ACH-2026-0001",
                "Acme Payroll",
                LocalDate.of(2026, 6, 5),
                validAccount(),
                List.of(new AchBatchEntryRequest(
                        "ENTRY-1",
                        "Jane Supplier",
                        new AccountReferenceRequest("CITIUS33", " ", "Jane Supplier"),
                        new MoneyRequest("USD", "10.999"),
                        "Payroll")));

        assertThat(paths(validate(request)))
                .contains("entries[0].receiverAccount.accountNumber", "entries[0].amount.value");
    }

    @Test
    void unknownTopLevelFieldFailsDeserialization() {
        assertThatThrownBy(() -> objectMapper.readValue("""
                {
                  "batchReference": "ACH-2026-0001",
                  "originatorName": "Acme Payroll",
                  "effectiveEntryDate": "2026-06-05",
                  "settlementAccount": {
                    "bankCode": "CITIUS33",
                    "accountNumber": "123456789",
                    "accountName": "Acme Operating"
                  },
                  "entries": [
                    {
                      "entryReference": "ENTRY-1",
                      "receiverName": "Jane Supplier",
                      "receiverAccount": {
                        "bankCode": "BOFAUS3N",
                        "accountNumber": "987654321",
                        "accountName": "Jane Supplier"
                      },
                      "amount": {
                        "currency": "USD",
                        "value": "1250.50"
                      },
                      "purpose": "Payroll"
                    }
                  ],
                  "unsupported": "value"
                }
                """, CreateAchBatchRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    @Test
    void unknownEntryFieldFailsDeserialization() {
        assertThatThrownBy(() -> objectMapper.readValue("""
                {
                  "batchReference": "ACH-2026-0001",
                  "originatorName": "Acme Payroll",
                  "effectiveEntryDate": "2026-06-05",
                  "settlementAccount": {
                    "bankCode": "CITIUS33",
                    "accountNumber": "123456789",
                    "accountName": "Acme Operating"
                  },
                  "entries": [
                    {
                      "entryReference": "ENTRY-1",
                      "receiverName": "Jane Supplier",
                      "receiverAccount": {
                        "bankCode": "BOFAUS3N",
                        "accountNumber": "987654321",
                        "accountName": "Jane Supplier"
                      },
                      "amount": {
                        "currency": "USD",
                        "value": "1250.50"
                      },
                      "purpose": "Payroll",
                      "unsupported": "value"
                    }
                  ]
                }
                """, CreateAchBatchRequest.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    private Set<ConstraintViolation<CreateAchBatchRequest>> validate(CreateAchBatchRequest request) {
        return validator.validate(request);
    }

    private Set<String> paths(Set<ConstraintViolation<CreateAchBatchRequest>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    private CreateAchBatchRequest validRequest() {
        return new CreateAchBatchRequest(
                "ACH-2026-0001",
                "Acme Payroll",
                LocalDate.of(2026, 6, 5),
                validAccount(),
                List.of(new AchBatchEntryRequest(
                        "ENTRY-1",
                        "Jane Supplier",
                        validAccount(),
                        new MoneyRequest("USD", "1250.50"),
                        "Payroll")));
    }

    private AccountReferenceRequest validAccount() {
        return new AccountReferenceRequest("CITIUS33", "123456789", "Acme Operating");
    }
}
