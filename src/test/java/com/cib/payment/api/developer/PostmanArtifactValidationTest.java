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
        assertThat(serialized).contains("success", "rejection", "timeout", "internal_failure");
        assertThat(serialized).contains("VALIDATION_ERROR", "IDEMPOTENCY_CONFLICT", "UNAUTHORIZED", "FORBIDDEN");

        assertThat(requestNames(collection)).contains(
                "Create Payment - Success",
                "Get Payment Status",
                "Create Payment - Rejection",
                "Create Payment - Timeout",
                "Create Payment - Internal Failure",
                "Create Payment - Invalid Request",
                "Create Payment - Authentication Failure",
                "Create Payment - Authorization Failure",
                "Create Payment - Idempotency Conflict Setup",
                "Create Payment - Idempotency Conflict");
        assertThat(savedExampleNames(collection)).contains(
                "202 Accepted - Success",
                "200 OK - Status Query",
                "400 Bad Request - Validation",
                "401 Unauthorized - Authentication",
                "403 Forbidden - Authorization",
                "409 Conflict - Idempotency",
                "202 Accepted - Rejection Scenario",
                "202 Accepted - Timeout Scenario",
                "202 Accepted - Internal Failure Scenario");

        assertThat(serialized).contains(
                "mock scenario terminal status is REJECTED",
                "mock scenario terminal status is TIMEOUT",
                "mock scenario terminal status is FAILED",
                "pm.sendRequest");
        assertThat(serialized).contains(
                "invalid request returns validation error",
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
    void postmanExamplesAlignWithOpenApiAndFixtures() throws Exception {
        var collection = Files.readString(COLLECTION, StandardCharsets.UTF_8);
        var openApi = new ClassPathResource("openapi/domestic-payment-api.yaml")
                .getContentAsString(StandardCharsets.UTF_8);
        var successFixture = new ClassPathResource("fixtures/domestic-payments/success-request.json")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(collection).contains("/v1/domestic-payments");
        assertThat(collection).contains("CreateDomesticPayment");
        assertThat(collection).contains("PaymentStatusResponse");
        assertThat(collection).contains("CIBBMYKL");
        assertThat(collection).contains("INV-2026-SUCCESS");
        assertThat(openApi).contains("X-Mock-Scenario");
        assertThat(successFixture).contains("\"paymentReference\": \"INV-2026-SUCCESS\"");
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
        assertThat(docs).contains("success", "rejection", "timeout", "internal_failure");
        assertThat(docs).contains("Postman");
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
