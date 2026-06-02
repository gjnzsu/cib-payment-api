package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class OpenApiContractTest {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Test
    void openApiContractDefinesPaymentEndpointsAndSchemas() throws Exception {
        var resource = new ClassPathResource("openapi/domestic-payment-api.yaml");
        var yaml = resource.getContentAsString(StandardCharsets.UTF_8);

        assertThat(yaml).contains("openapi: 3.0.3");
        assertThat(yaml).contains("/v1/domestic-payments:");
        assertThat(yaml).contains("/v1/domestic-payments/{paymentId}:");
        assertThat(yaml).contains("application/pain.001+xml");
        assertThat(yaml).contains("application/pain.002+xml");
        assertThat(yaml).contains("pain.001.001.09");
        assertThat(yaml).contains("pain.002.001.10");
        assertThat(yaml).contains("ACSC", "RJCT", "PDNG");
        assertThat(yaml).contains("HKD-only");
        assertThat(yaml).doesNotContain("CreateDomesticPaymentRequest");
        assertThat(yaml).doesNotContain("\n    PaymentResponse:");
        assertThat(yaml).doesNotContain("\n    PaymentStatusResponse:");
        assertThat(yaml).contains("ErrorResponse");
        assertThat(yaml).contains("X-Correlation-ID");
        assertThat(yaml).contains("X-Mock-Scenario");
        assertThat(yaml).contains("suspicious_proxy_or_account", "pending");
        assertThat(yaml).contains("pacs.008 is internal only");
    }

    @Test
    void openApiContractDefinesFiCorrespondentPaymentAndInvestigationArtifacts() throws Exception {
        var resource = new ClassPathResource("openapi/domestic-payment-api.yaml");
        var yaml = resource.getContentAsString(StandardCharsets.UTF_8);

        assertThat(yaml).contains("/v1/fi-payments:");
        assertThat(yaml).contains("/v1/fi-payments/{paymentId}:");
        assertThat(yaml).contains("/v1/fi-payments/{paymentId}/recall-requests:");
        assertThat(yaml).contains("application/pacs.009+xml");
        assertThat(yaml).contains("application/camt.056+xml");
        assertThat(yaml).contains("application/camt.029+xml");
        assertThat(yaml).contains("FiPaymentAcknowledgementResponse");
        assertThat(yaml).contains("FiPaymentStatusResponse");
        assertThat(yaml).contains("CorrespondentSettlementContext");
        assertThat(yaml).contains("RecallInvestigationSummary");
        assertThat(yaml).contains("fi-payments:create", "fi-payments:read", "fi-payments:investigate");
        assertThat(yaml).contains("FI status query does not require Idempotency-Key");
        assertThat(yaml).contains("fi_payment_accepted");
        assertThat(yaml).contains("fi_payment_rejected_unsupported_correspondent");
        assertThat(yaml).contains("fi_payment_pending_correspondent_review");
        assertThat(yaml).contains("recall_accepted", "recall_rejected", "investigation_pending");
        assertThat(yaml).contains("USD-only");
        assertThat(yaml).contains("simulator-only");
        assertThat(yaml).contains("no real SWIFT, CBPR+, correspondent banking, ledger, AML, sanctions, fraud, or settlement connectivity");
        assertThat(yaml).contains("NOSTRO", "VOSTRO", "LORO");
        assertThat(yaml).contains("maskedSimulatedAccountReference");
    }

    @Test
    void openApiStructurallyDefinesFiOperationsSecurityMediaTypesAndSchemas() throws Exception {
        var openApi = readOpenApi();
        var createFiPayment = operation(openApi, "/v1/fi-payments", "post");
        var getFiPaymentStatus = operation(openApi, "/v1/fi-payments/{paymentId}", "get");
        var createFiRecall = operation(openApi, "/v1/fi-payments/{paymentId}/recall-requests", "post");

        assertThat(openApi.path("openapi").asText()).isEqualTo("3.0.3");

        assertBearerSecurityAndScope(createFiPayment, "fi-payments:create");
        assertParameterRefs(createFiPayment, "IdempotencyKey", "CorrelationId", "FiMockScenario");
        assertThat(createFiPayment.path("requestBody").path("required").asBoolean()).isTrue();
        assertThat(createFiPayment.at("/requestBody/content").has("application/pacs.009+xml")).isTrue();
        assertThat(createFiPayment.at("/responses/202/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/FiPaymentAcknowledgementResponse");

        assertBearerSecurityAndScope(getFiPaymentStatus, "fi-payments:read");
        assertPathParameter(getFiPaymentStatus, "paymentId");
        assertParameterRefs(getFiPaymentStatus, "CorrelationId");
        assertThat(parameterRefs(getFiPaymentStatus)).doesNotContain("IdempotencyKey");
        assertThat(getFiPaymentStatus.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/FiPaymentStatusResponse");

        assertBearerSecurityAndScope(createFiRecall, "fi-payments:investigate");
        assertPathParameter(createFiRecall, "paymentId");
        assertParameterRefs(createFiRecall, "IdempotencyKey", "CorrelationId", "FiRecallMockScenario");
        assertThat(createFiRecall.path("requestBody").path("required").asBoolean()).isTrue();
        assertThat(createFiRecall.at("/requestBody/content").has("application/camt.056+xml")).isTrue();
        assertThat(createFiRecall.at("/responses/202/content").has("application/camt.029+xml")).isTrue();

        var components = openApi.at("/components/schemas");
        assertRequiredFields(
                components.path("FiPaymentStatusResponse"),
                "paymentId",
                "status",
                "messageId",
                "instructionId",
                "originalPaymentReference",
                "instructingAgentBic",
                "instructedAgentBic",
                "settlementCurrency",
                "correspondentSettlementContext",
                "correlationId",
                "links");
        assertRequiredFields(
                components.path("CorrespondentSettlementContext"),
                "instructingAgentBic",
                "instructedAgentBic",
                "settlementCurrency",
                "accountRelationshipRole",
                "maskedSimulatedAccountReference");
        assertThat(components.at("/CorrespondentSettlementContext/properties/settlementCurrency/enum/0").asText())
                .isEqualTo("USD");
        assertThat(textValues(components.at("/CorrespondentSettlementContext/properties/accountRelationshipRole/enum")))
                .containsExactly("NOSTRO", "VOSTRO", "LORO");
        assertThat(components.has("RecallInvestigationSummary")).isTrue();
    }

    @Test
    void openApiStructurallyDefinesAchAndRtgsOperationsSecurityScenariosAndSchemas() throws Exception {
        var openApi = readOpenApi();
        var createAchBatch = operation(openApi, "/v1/ach-batches", "post");
        var getAchBatchStatus = operation(openApi, "/v1/ach-batches/{batchId}", "get");
        var createRtgsPayment = operation(openApi, "/v1/rtgs-payments", "post");
        var getRtgsPaymentStatus = operation(openApi, "/v1/rtgs-payments/{paymentId}", "get");

        assertBearerSecurityAndScope(createAchBatch, "ach-batches:create");
        assertParameterRefs(createAchBatch, "IdempotencyKey", "CorrelationId", "AchMockScenario");
        assertThat(createAchBatch.path("requestBody").path("required").asBoolean()).isTrue();
        assertThat(createAchBatch.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/CreateAchBatchRequest");
        assertThat(createAchBatch.at("/responses/202/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/AchBatchResponse");
        assertStandardJsonErrors(createAchBatch, "400", "401", "403", "409", "422", "500");

        assertBearerSecurityAndScope(getAchBatchStatus, "ach-batches:read");
        assertPathParameter(getAchBatchStatus, "batchId");
        assertParameterRefs(getAchBatchStatus, "CorrelationId");
        assertThat(parameterRefs(getAchBatchStatus)).doesNotContain("IdempotencyKey");
        assertThat(getAchBatchStatus.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/AchBatchStatusResponse");
        assertStandardJsonErrors(getAchBatchStatus, "400", "401", "403", "404", "500");

        assertBearerSecurityAndScope(createRtgsPayment, "rtgs-payments:create");
        assertParameterRefs(createRtgsPayment, "IdempotencyKey", "CorrelationId", "RtgsMockScenario");
        assertThat(createRtgsPayment.path("requestBody").path("required").asBoolean()).isTrue();
        assertThat(createRtgsPayment.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/CreateRtgsPaymentRequest");
        assertThat(createRtgsPayment.at("/responses/202/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/RtgsPaymentResponse");
        assertStandardJsonErrors(createRtgsPayment, "400", "401", "403", "409", "422", "500");

        assertBearerSecurityAndScope(getRtgsPaymentStatus, "rtgs-payments:read");
        assertPathParameter(getRtgsPaymentStatus, "paymentId");
        assertParameterRefs(getRtgsPaymentStatus, "CorrelationId");
        assertThat(parameterRefs(getRtgsPaymentStatus)).doesNotContain("IdempotencyKey");
        assertThat(getRtgsPaymentStatus.at("/responses/200/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/RtgsPaymentStatusResponse");
        assertStandardJsonErrors(getRtgsPaymentStatus, "400", "401", "403", "404", "500");

        var parameters = openApi.at("/components/parameters");
        assertThat(textValues(parameters.at("/AchMockScenario/schema/enum"))).containsExactly(
                "ach_direct_credit_accepted",
                "ach_direct_credit_settled",
                "ach_direct_credit_settlement_pending",
                "ach_direct_credit_partially_returned",
                "ach_direct_credit_rejected");
        assertThat(textValues(parameters.at("/RtgsMockScenario/schema/enum"))).containsExactly(
                "rtgs_settled",
                "rtgs_queued_for_liquidity",
                "rtgs_rejected");

        var components = openApi.at("/components/schemas");
        assertRequiredFields(
                components.path("CreateAchBatchRequest"),
                "batchReference",
                "originatorName",
                "effectiveEntryDate",
                "settlementAccount",
                "entries");
        assertRequiredFields(
                components.path("AchBatchStatusResponse"),
                "batchId",
                "rail",
                "batchReference",
                "originatorName",
                "effectiveEntryDate",
                "status",
                "entryCount",
                "totalAmount",
                "entries",
                "correlationId",
                "links");
        assertRequiredFields(
                components.path("AchBatchEntryStatusResponse"),
                "entryId",
                "entryReference",
                "receiverName",
                "amount",
                "status");
        assertThat(textValues(components.at("/AchBatchEntryStatusResponse/properties/status/enum")))
                .contains("ACCEPTED", "SETTLED", "RETURNED", "REJECTED");

        assertRequiredFields(
                components.path("CreateRtgsPaymentRequest"),
                "paymentReference",
                "clientSegment",
                "amount",
                "requestedSettlementDate",
                "settlementPriority",
                "purpose");
        assertThat(textValues(components.at("/CreateRtgsPaymentRequest/properties/clientSegment/enum")))
                .containsExactly("CORPORATE", "FI");
        assertRequiredFields(
                components.path("RtgsPaymentStatusResponse"),
                "paymentId",
                "rail",
                "paymentReference",
                "clientSegment",
                "amount",
                "requestedSettlementDate",
                "settlementPriority",
                "purpose",
                "status",
                "settlementFinality",
                "correlationId",
                "links");
        assertThat(textValues(components.at("/RtgsPaymentStatusResponse/properties/status/enum")))
                .contains("ACCEPTED_FOR_SETTLEMENT", "QUEUED_FOR_LIQUIDITY", "SETTLED", "REJECTED");
        assertThat(components.at("/RtgsPaymentStatusResponse/properties/settlementFinality/type").asText())
                .isEqualTo("boolean");

        var yaml = new ClassPathResource("openapi/domestic-payment-api.yaml")
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(yaml).contains(
                "simulator-only",
                "no real ACH or RTGS connectivity",
                "no NACHA or ISO 20022 runtime for ACH or RTGS",
                "no central bank ledger, liquidity, queue management, or settlement connectivity");
    }

    private JsonNode readOpenApi() throws Exception {
        var resource = new ClassPathResource("openapi/domestic-payment-api.yaml");
        return yamlMapper.readTree(resource.getInputStream());
    }

    private JsonNode operation(JsonNode openApi, String path, String method) {
        var operation = openApi.path("paths").path(path).path(method);
        assertThat(operation.isMissingNode()).as(method.toUpperCase() + " " + path).isFalse();
        return operation;
    }

    private void assertBearerSecurityAndScope(JsonNode operation, String requiredScope) {
        assertThat(operation.at("/security/0").has("bearerAuth")).isTrue();
        assertThat(textValues(operation.path("x-required-scopes"))).containsExactly(requiredScope);
    }

    private void assertParameterRefs(JsonNode operation, String... expectedRefs) {
        assertThat(parameterRefs(operation)).contains(expectedRefs);
    }

    private Set<String> parameterRefs(JsonNode operation) {
        var refs = new java.util.HashSet<String>();
        operation.path("parameters").forEach(parameter -> {
            var ref = parameter.path("$ref").asText();
            if (!ref.isBlank()) {
                refs.add(ref.substring(ref.lastIndexOf('/') + 1));
            }
        });
        return refs;
    }

    private void assertPathParameter(JsonNode operation, String name) {
        assertThat(operation.path("parameters"))
                .anySatisfy(parameter -> {
                    assertThat(parameter.path("name").asText()).isEqualTo(name);
                    assertThat(parameter.path("in").asText()).isEqualTo("path");
                    assertThat(parameter.path("required").asBoolean()).isTrue();
                });
    }

    private void assertStandardJsonErrors(JsonNode operation, String... statuses) {
        for (var status : statuses) {
            var response = operation.path("responses").path(status);
            assertThat(response.path("$ref").asText()).as("response " + status).startsWith("#/components/responses/");
        }
    }

    private void assertRequiredFields(JsonNode schema, String... expectedFields) {
        assertThat(textValues(schema.path("required"))).contains(expectedFields);
    }

    private java.util.List<String> textValues(JsonNode arrayNode) {
        var values = new java.util.ArrayList<String>();
        arrayNode.forEach(value -> values.add(value.asText()));
        return values;
    }
}
