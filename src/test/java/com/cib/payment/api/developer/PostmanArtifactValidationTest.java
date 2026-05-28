package com.cib.payment.api.developer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PostmanArtifactValidationTest {
    private static final Path COLLECTION = Path.of("postman", "domestic-rtp-payment-api.postman_collection.json");
    private static final Path ENVIRONMENT = Path.of("postman", "domestic-rtp-payment-api.local.postman_environment.json");
    private static final Path DOCS = Path.of("docs", "developer-support", "postman-local-testing.md");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void postmanCollectionCoversContractEndpointsHeadersScenariosAndExamples() throws Exception {
        var collection = objectMapper.readTree(Files.readString(COLLECTION, StandardCharsets.UTF_8));
        var serialized = collection.toString();

        assertThat(collection.has("auth")).isFalse();
        assertThat(serialized).contains("POST", "/v1/domestic-payments");
        assertThat(serialized).contains("GET", "/v1/domestic-payments", "{{paymentId}}");
        assertThat(serialized).contains("Authorization", "Bearer {{jwtToken}}");
        assertThat(serialized).contains("Idempotency-Key", "{{idempotencyKey}}");
        assertThat(serialized).contains("X-Correlation-ID", "{{correlationId}}");
        assertThat(serialized).contains("X-Mock-Scenario", "{{mockScenario}}");
        assertThat(serialized).contains("application/pain.001+xml", "application/pain.002+xml");
        assertThat(serialized).contains("pain.001.001.09", "pain.002.001.10");
        assertThat(serialized).contains("CIBBHKHH", "SUPPHKHH", "AcctSvcrRef");
        assertThat(serialized).contains("success", "rejection", "suspicious_proxy_or_account", "pending", "timeout", "internal_failure");
        assertThat(serialized).contains("ACSC", "RJCT", "PDNG");
        assertThat(serialized).doesNotContain("CreateDomesticPaymentRequest");
        assertThat(serialized).doesNotContain("\"currency\": \"MYR\"");
        assertThat(serialized).doesNotContain("\"debtorAccount\"");
        assertThat(serialized).contains("VALIDATION_ERROR", "IDEMPOTENCY_CONFLICT", "UNAUTHORIZED", "FORBIDDEN");

        assertThat(requestNames(collection)).contains(
                "Create Payment - Success",
                "Get Payment Status",
                "Create Payment - Rejection",
                "Create Payment - Suspicious Proxy Or Account",
                "Create Payment - Pending",
                "Create Payment - Timeout",
                "Create Payment - Internal Failure",
                "Create Payment - Malformed XML",
                "Create Payment - Non-HKD Profile Failure",
                "Create Payment - Replay",
                "Create Payment - Authentication Failure",
                "Create Payment - Authorization Failure",
                "Create Payment - Idempotency Conflict Setup",
                "Create Payment - Idempotency Conflict");
        assertThat(savedExampleNames(collection)).contains(
                "200 OK - ACSC",
                "200 OK - Status Query ACSC",
                "400 Bad Request - Validation",
                "422 Unprocessable Entity - HK Profile",
                "401 Unauthorized - Authentication",
                "403 Forbidden - Authorization",
                "409 Conflict - Idempotency",
                "200 OK - RJCT Scenario",
                "200 OK - PDNG Timeout Scenario",
                "200 OK - PDNG Internal Failure Scenario");

        assertThat(serialized).contains(
                "ISO status is RJCT",
                "ISO status is PDNG",
                "pm.sendRequest");
        assertThat(serialized).contains(
                "malformed XML returns validation error",
                "non-HKD profile failure returns semantic error",
                "idempotent replay returns the original pain.002",
                "Bearer invalid-local-test-token",
                "\"Authorization\",\"value\":\"Bearer {{readOnlyJwtToken}}",
                "Set readOnlyJwtToken to a JWT generated with only payments:read",
                "readOnlyJwtToken must not include payments:create",
                "pm.request.headers.upsert",
                "Run this request first to create the original payment for idempotency conflict testing",
                "authentication failure returns unauthorized",
                "authorization failure returns forbidden",
                "idempotency conflict returns conflict");
    }

    @Test
    void postmanEnvironmentProvidesRequiredLocalVariables() throws Exception {
        var environment = objectMapper.readTree(Files.readString(ENVIRONMENT, StandardCharsets.UTF_8));

        assertThat(environmentVariables(environment)).contains(
                "baseUrl",
                "jwtToken",
                "readOnlyJwtToken",
                "correlationId",
                "idempotencyKey",
                "conflictIdempotencyKey",
                "paymentId",
                "mockScenario");
    }

    @Test
    void postmanCollectionCoversFiCorrespondentPaymentAndInvestigationScenarios() throws Exception {
        var collection = objectMapper.readTree(Files.readString(COLLECTION, StandardCharsets.UTF_8));
        var serialized = collection.toString();

        assertThat(serialized).contains("/v1/fi-payments");
        assertThat(serialized).contains("/v1/fi-payments/{{fiPaymentId}}");
        assertThat(serialized).contains("/v1/fi-payments/{{fiPaymentId}}/recall-requests");
        assertThat(serialized).contains("application/pacs.009+xml");
        assertThat(serialized).contains("application/camt.056+xml");
        assertThat(serialized).contains("application/camt.029+xml");
        assertThat(serialized).contains("Bearer {{fiJwtToken}}", "Bearer {{fiReadOnlyJwtToken}}");
        assertThat(serialized).contains("Idempotency-Key", "{{fiIdempotencyKey}}", "{{fiRecallIdempotencyKey}}");
        assertThat(serialized).contains("X-Mock-Scenario", "{{fiMockScenario}}");
        assertThat(serialized).contains(
                "fi_payment_accepted",
                "fi_payment_rejected_unsupported_correspondent",
                "fi_payment_pending_correspondent_review",
                "recall_accepted",
                "recall_rejected",
                "investigation_pending");
        assertThat(serialized).contains("FICLIENT01", "FI-E2E-20260528-0001");
        assertThat(serialized).contains("SETTLED", "REJECTED", "PROCESSING");
        assertThat(serialized).contains("NOSTRO", "VOSTRO", "LORO");
        assertThat(serialized).contains("VALIDATION_ERROR", "IDEMPOTENCY_CONFLICT", "UNAUTHORIZED", "FORBIDDEN");
        assertThat(serialized).contains("X-Correlation-ID");

        assertThat(requestNames(collection)).contains(
                "Create FI Payment - Accepted",
                "Get FI Payment Status",
                "Create FI Recall - Accepted",
                "Create FI Recall - Rejected",
                "Create FI Recall - Investigation Pending",
                "Create FI Payment - Replay",
                "Create FI Payment - Idempotency Conflict Setup",
                "Create FI Payment - Idempotency Conflict",
                "Create FI Payment - Authentication Failure",
                "Create FI Payment - Scope Failure",
                "Create FI Payment - Validation Failure");
        assertThat(savedExampleNames(collection)).contains(
                "202 Accepted - FI Payment SETTLED",
                "200 OK - FI Status SETTLED",
                "202 Accepted - camt.029 Recall Accepted",
                "202 Accepted - camt.029 Recall Rejected",
                "202 Accepted - camt.029 Investigation Pending",
                "400 Bad Request - FI Validation",
                "401 Unauthorized - FI Authentication",
                "403 Forbidden - FI Scope",
                "409 Conflict - FI Idempotency");
        assertThat(serialized).contains(
                "FI payment acknowledgement contains JSON status and correlation",
                "FI status query returns JSON without Idempotency-Key",
                "FI recall response is camt.029",
                "FI idempotent replay returns the original acknowledgement",
                "FI idempotency conflict returns conflict",
                "FI authentication failure returns unauthorized",
                "FI scope failure returns forbidden",
                "FI validation failure returns validation error");
    }

    @Test
    void postmanEnvironmentProvidesFiLocalVariables() throws Exception {
        var environment = objectMapper.readTree(Files.readString(ENVIRONMENT, StandardCharsets.UTF_8));

        assertThat(environmentVariables(environment)).contains(
                "fiJwtToken",
                "fiReadOnlyJwtToken",
                "fiInvestigateToken",
                "fiIdempotencyKey",
                "fiRecallIdempotencyKey",
                "fiConflictIdempotencyKey",
                "fiPaymentId",
                "fiMockScenario",
                "fiRecallScenario");
    }

    @Test
    void postmanExamplesAlignWithOpenApiAndFixtures() throws Exception {
        var collection = Files.readString(COLLECTION, StandardCharsets.UTF_8);
        var openApi = new ClassPathResource("openapi/domestic-payment-api.yaml")
                .getContentAsString(StandardCharsets.UTF_8);
        var successFixture = new ClassPathResource("iso/pain001-success.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(collection).contains("/v1/domestic-payments");
        assertThat(collection).contains("CreateDomesticPayment");
        assertThat(collection).contains("pain.001.001.09", "pain.002.001.10");
        assertThat(collection).contains("MSG-20260524-0001");
        assertThat(collection).contains("INV-2026-0001");
        assertThat(openApi).contains("X-Mock-Scenario");
        assertThat(openApi).contains("application/pain.001+xml", "application/pain.002+xml");
        assertThat(successFixture).contains("<EndToEndId>INV-2026-0001</EndToEndId>");
    }

    @Test
    void fiPostmanExamplesAlignWithOpenApiAndFixtures() throws Exception {
        var collection = Files.readString(COLLECTION, StandardCharsets.UTF_8);
        var openApi = new ClassPathResource("openapi/domestic-payment-api.yaml")
                .getContentAsString(StandardCharsets.UTF_8);
        var pacs009 = new ClassPathResource("fi/pacs009-accepted-nostro.xml")
                .getContentAsString(StandardCharsets.UTF_8);
        var camt056 = new ClassPathResource("fi/camt056-recall-accepted.xml")
                .getContentAsString(StandardCharsets.UTF_8);
        var camt029 = new ClassPathResource("fi/camt029-accepted.xml")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(collection).contains("/v1/fi-payments");
        assertThat(collection).contains("CreateFiPayment", "GetFiPaymentStatus", "CreateFiRecallInvestigation");
        assertThat(collection).contains("pacs.009.001.08", "camt.056.001.08", "camt.029.001.09");
        assertThat(collection).contains(
                "FI-MSG-20260528-ACCEPTED-NOSTRO",
                "FI-E2E-20260528-0001",
                "FICLIENT01-CAMT056-RECALL-ACCEPTED");
        assertThat(openApi).contains("application/pacs.009+xml", "application/camt.056+xml", "application/camt.029+xml");
        assertThat(openApi).contains("fi-payments:create", "fi-payments:read", "fi-payments:investigate");
        assertThat(pacs009).contains("<MsgId>FI-MSG-20260528-ACCEPTED-NOSTRO</MsgId>");
        assertThat(camt056).contains("<Id>FICLIENT01-CAMT056-RECALL-ACCEPTED</Id>");
        assertThat(camt029).contains("<CxlStsId>FICLIENT01-CANCEL-ACCEPTED</CxlStsId>");
    }


    @Test
    void localPostmanDocumentationExplainsRunDocsJwtMockScenariosAndCollectionUsage() throws Exception {
        var docs = Files.readString(DOCS, StandardCharsets.UTF_8);

        assertThat(docs).contains("mvn spring-boot:run");
        assertThat(docs).contains("/swagger-ui/index.html");
        assertThat(docs).contains("/openapi/domestic-payment-api.yaml");
        assertThat(docs).contains("jwtToken");
        assertThat(docs).contains("LocalJwtTokenGenerator");
        assertThat(docs).contains("readOnlyJwtToken");
        assertThat(docs).contains("X-Mock-Scenario");
        assertThat(docs).contains("pain.001.001.09", "pain.002.001.10");
        assertThat(docs).contains("ACSC", "RJCT", "PDNG");
        assertThat(docs).contains("success", "rejection", "suspicious_proxy_or_account", "pending", "timeout", "internal_failure");
        assertThat(docs).contains("simulator-only", "no real HKICL/FPS connectivity", "pacs.008 is internal-only");
        assertThat(docs).contains("Postman");
    }

    @Test
    void localPostmanDocumentationExplainsFiTokenProfileAccountContextAndSandboxBoundary() throws Exception {
        var docs = Files.readString(DOCS, StandardCharsets.UTF_8);
        var strategy = Files.readString(
                Path.of("docs", "product-strategy", "payment-simulation-suite-vision.md"),
                StandardCharsets.UTF_8);

        assertThat(docs).contains("fi-payments:create", "fi-payments:read", "fi-payments:investigate");
        assertThat(docs).contains("fiJwtToken", "fiReadOnlyJwtToken", "fiInvestigateToken");
        assertThat(docs).contains("application/pacs.009+xml", "application/camt.056+xml", "application/camt.029+xml");
        assertThat(docs).contains("USD-only");
        assertThat(docs).contains("NOSTRO", "VOSTRO", "LORO");
        assertThat(docs).contains("masked simulated account reference");
        assertThat(docs).contains("no real ledger", "no real settlement");
        assertThat(docs).contains("baas-api-sandbox", "future scenario-pack integration", "not part of this runtime change");
        assertThat(strategy).contains("baas-api-sandbox", "future scenario-pack integration", "not part of this runtime change");
    }


    private Set<String> requestNames(JsonNode node) {
        var names = new HashSet<String>();
        collectNames(node.path("item"), names);
        return names;
    }

    private Set<String> savedExampleNames(JsonNode node) {
        var names = new HashSet<String>();
        collectResponseNames(node.path("item"), names);
        return names;
    }

    private Set<String> environmentVariables(JsonNode environment) {
        var variables = new HashSet<String>();
        environment.path("values").forEach(value -> variables.add(value.path("key").asText()));
        return variables;
    }

    private void collectNames(JsonNode items, Set<String> names) {
        items.forEach(item -> {
            if (item.has("request")) {
                names.add(item.path("name").asText());
            }
            collectNames(item.path("item"), names);
        });
    }

    private void collectResponseNames(JsonNode items, Set<String> names) {
        items.forEach(item -> {
            item.path("response").forEach(response -> names.add(response.path("name").asText()));
            collectResponseNames(item.path("item"), names);
        });
    }
}
