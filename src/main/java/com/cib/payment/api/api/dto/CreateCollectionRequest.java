package com.cib.payment.api.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = false)
public record CreateCollectionRequest(
        @NotBlank String collectionProfile,
        @NotBlank String collectionReference,
        @NotBlank String mandateReference,
        @NotBlank String creditorName,
        @NotBlank String debtorName,
        @Valid AccountReferenceRequest settlementAccount,
        String payerBankCode,
        String payerAlias,
        @Valid MoneyRequest amount,
        String purpose,
        @NotNull List<@Valid @NotNull CollectionEntryRequest> entries) {}
