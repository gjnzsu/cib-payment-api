package com.cib.payment.api.developer;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.application.port.HkClearingSettlementOutcome;
import com.cib.payment.api.application.service.FiPaymentAdmissionService;
import com.cib.payment.api.application.service.IsoPaymentAdmissionService;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.InternalInterbankTransfer;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.infrastructure.iso.Camt056Parser;
import com.cib.payment.api.infrastructure.iso.Pain001Parser;
import com.cib.payment.api.infrastructure.iso.Pacs009Parser;
import com.cib.payment.api.infrastructure.simulator.DeterministicHkClearingSettlementSimulator;
import com.cib.payment.api.infrastructure.simulator.FiCorrespondentRouteProfile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PostmanArtifactValidationTest {
    private static final Path COLLECTION = Path.of("postman", "domestic-rtp-payment-api.postman_collection.json");
    private static final Path ENVIRONMENT = Path.of("postman", "domestic-rtp-payment-api.local.postman_environment.json");
    private static final Path README = Path.of("README.md");
    private static final Path DOCS = Path.of("docs", "developer-support", "postman-local-testing.md");
    private static final String ORIGINAL_FI_REFERENCE = "FI-E2E-20260528-0001";
    private static final Set<String> SUPPORTED_FI_RECALL_REASONS = Set.of("DUPL", "CUST", "AM09", "FRAD", "TECH");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Pain001Parser pain001Parser = new Pain001Parser();
    private final IsoPaymentAdmissionService isoPaymentAdmissionService = new IsoPaymentAdmissionService(pain001Parser);
    private final DeterministicHkClearingSettlementSimulator hkSimulator =
            new DeterministicHkClearingSettlementSimulator();
    private final Pacs009Parser pacs009Parser = new Pacs009Parser();
    private final FiPaymentAdmissionService fiPaymentAdmissionService = new FiPaymentAdmissionService(pacs009Parser);
    private final FiCorrespondentRouteProfile routeProfile = new FiCorrespondentRouteProfile();
    private final Camt056Parser camt056Parser = new Camt056Parser();

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
                "200 OK - RJCT Internal Failure Scenario");

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
        assertThat(serialized).contains(
                "FICLIENT01-CANCEL-ACCEPTED",
                "FICLIENT01-CANCEL-REJECTED",
                "FICLIENT01-CANCEL-PENDING",
                "ACCP",
                "RJCR",
                "PDCR",
                "NOAS",
                "IPAY");
        assertThat(serialized).contains(
                "VALIDATION_ERROR", "SEMANTIC_PAYMENT_ERROR", "IDEMPOTENCY_CONFLICT", "UNAUTHORIZED", "FORBIDDEN");
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
    void postmanCollectionCoversClassicPaymentRailSimulationJourney() throws Exception {
        var collection = objectMapper.readTree(Files.readString(COLLECTION, StandardCharsets.UTF_8));
        var serialized = collection.toString();

        assertThat(folderNames(collection)).contains("Classic Payment Rail Simulation");
        assertThat(requestNames(collection)).contains(
                "RTP Baseline - Create Payment",
                "RTP Baseline - Get Payment Status",
                "ACH Direct Credit - Settled",
                "ACH Direct Credit - Get Batch Status",
                "ACH Direct Credit - Partially Returned",
                "ACH Direct Credit - Rejected",
                "Corporate RTGS - Settled",
                "FI RTGS - Settled",
                "FI RTGS - Queued For Liquidity",
                "RTGS - Rejected",
                "RTGS - Get Payment Status",
                "FI Correspondent Comparison - Existing Arrangement");

        assertClassicRailCreateRequest(collection, "ACH Direct Credit - Settled",
                "/v1/ach-batches", "{{achJwtToken}}", "{{achSettledIdempotencyKey}}",
                "ach_direct_credit_settled", "SETTLED");
        assertClassicRailCreateRequest(collection, "ACH Direct Credit - Partially Returned",
                "/v1/ach-batches", "{{achJwtToken}}", "{{achPartiallyReturnedIdempotencyKey}}",
                "ach_direct_credit_partially_returned", "PARTIALLY_RETURNED");
        assertClassicRailCreateRequest(collection, "ACH Direct Credit - Rejected",
                "/v1/ach-batches", "{{achJwtToken}}", "{{achRejectedIdempotencyKey}}",
                "ach_direct_credit_rejected", "REJECTED");
        assertClassicRailStatusRequest(collection, "ACH Direct Credit - Get Batch Status",
                "/v1/ach-batches/{{achBatchId}}", "{{achReadOnlyJwtToken}}");

        assertClassicRailCreateRequest(collection, "Corporate RTGS - Settled",
                "/v1/rtgs-payments", "{{rtgsJwtToken}}", "{{rtgsCorporateSettledIdempotencyKey}}",
                "rtgs_settled", "SETTLED");
        assertClassicRailCreateRequest(collection, "FI RTGS - Settled",
                "/v1/rtgs-payments", "{{rtgsJwtToken}}", "{{rtgsFiSettledIdempotencyKey}}",
                "rtgs_settled", "SETTLED");
        assertClassicRailCreateRequest(collection, "FI RTGS - Queued For Liquidity",
                "/v1/rtgs-payments", "{{rtgsJwtToken}}", "{{rtgsFiQueuedIdempotencyKey}}",
                "rtgs_queued_for_liquidity", "QUEUED_FOR_LIQUIDITY");
        assertClassicRailCreateRequest(collection, "RTGS - Rejected",
                "/v1/rtgs-payments", "{{rtgsJwtToken}}", "{{rtgsRejectedIdempotencyKey}}",
                "rtgs_rejected", "REJECTED");
        assertClassicRailStatusRequest(collection, "RTGS - Get Payment Status",
                "/v1/rtgs-payments/{{rtgsPaymentId}}", "{{rtgsReadOnlyJwtToken}}");
        assertClassicRailCreateRequestsUseDistinctIdempotencyKeys(collection);

        assertThat(requestDescription(collection, "FI Correspondent Comparison - Existing Arrangement"))
                .contains("separate arrangement", "not necessarily RTP/ACH/RTGS", "nostro", "vostro", "loro");
        assertThat(serialized).contains(
                "Expected HTTP status: 202",
                "Expected HTTP status: 200",
                "expected lifecycle status",
                "entry-level status summary",
                "settlementFinality",
                "clientSegment",
                "payments:create",
                "ach-batches:create",
                "ach-batches:read",
                "rtgs-payments:create",
                "rtgs-payments:read");
    }

    @Test
    void postmanEnvironmentProvidesClassicPaymentRailVariables() throws Exception {
        var environment = objectMapper.readTree(Files.readString(ENVIRONMENT, StandardCharsets.UTF_8));

        assertThat(environmentVariables(environment)).contains(
                "achBatchId",
                "achIdempotencyKey",
                "achSettledIdempotencyKey",
                "achPartiallyReturnedIdempotencyKey",
                "achRejectedIdempotencyKey",
                "rtgsPaymentId",
                "rtgsIdempotencyKey",
                "rtgsCorporateSettledIdempotencyKey",
                "rtgsFiSettledIdempotencyKey",
                "rtgsFiQueuedIdempotencyKey",
                "rtgsRejectedIdempotencyKey",
                "railMockScenario",
                "achJwtToken",
                "achReadOnlyJwtToken",
                "rtgsJwtToken",
                "rtgsReadOnlyJwtToken");

        assertThat(List.of(
                        environmentVariableValue(environment, "achSettledIdempotencyKey"),
                        environmentVariableValue(environment, "achPartiallyReturnedIdempotencyKey"),
                        environmentVariableValue(environment, "achRejectedIdempotencyKey"),
                        environmentVariableValue(environment, "rtgsCorporateSettledIdempotencyKey"),
                        environmentVariableValue(environment, "rtgsFiSettledIdempotencyKey"),
                        environmentVariableValue(environment, "rtgsFiQueuedIdempotencyKey"),
                        environmentVariableValue(environment, "rtgsRejectedIdempotencyKey")))
                .as("Classic rail per-scenario idempotency key values")
                .doesNotContain("")
                .doesNotHaveDuplicates();
    }

    @Test
    void fiRecallScenariosDocumentOneRecallRecordPerFreshPayment() throws Exception {
        var collection = objectMapper.readTree(Files.readString(COLLECTION, StandardCharsets.UTF_8));
        var docs = Files.readString(DOCS, StandardCharsets.UTF_8);

        assertThat(docs).contains(
                "one recall or investigation record per FI payment",
                "fresh FI payment",
                "Do not run the accepted, rejected, and pending recall requests sequentially against the same fiPaymentId");
        assertThat(requestDescription(collection, "Create FI Recall - Accepted")).contains(
                "requires a fresh FI payment",
                "do not run another recall scenario against the same payment ID");
        assertThat(requestDescription(collection, "Create FI Recall - Rejected")).contains(
                "Create a fresh FI payment",
                "before running this rejected recall scenario");
        assertThat(requestDescription(collection, "Create FI Recall - Investigation Pending")).contains(
                "Create a fresh FI payment",
                "before running this pending investigation scenario");
    }

    @Test
    void fiJsonSavedExamplesIncludeFullCorrespondentSettlementContextShape() throws Exception {
        var collection = objectMapper.readTree(Files.readString(COLLECTION, StandardCharsets.UTF_8));

        assertFiCorrespondentContext(savedJsonExample(collection, "202 Accepted - FI Payment SETTLED"));
        assertFiCorrespondentContext(savedJsonExample(collection, "200 OK - FI Status SETTLED"));
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
    void fiOpenApiXmlExamplesAreExecutableAgainstRuntimeParsersAndRouteProfile() throws Exception {
        var openApi = readOpenApi();
        var pacs009 = openApiRequestExample(
                openApi,
                "/v1/fi-payments",
                "post",
                "application/pacs.009+xml",
                "accepted");
        var camt056 = openApiRequestExample(
                openApi,
                "/v1/fi-payments/{paymentId}/recall-requests",
                "post",
                "application/camt.056+xml",
                "recallAccepted");

        assertPacs009AcceptedByRuntimeContract(pacs009);
        assertCamt056AcceptedByRuntimeParser(camt056);
    }

    @Test
    void fiPostmanPacs009BodiesForCreateReplayAndConflictAreExecutableAgainstRuntimeContract() throws Exception {
        var collection = objectMapper.readTree(Files.readString(COLLECTION, StandardCharsets.UTF_8));

        for (var requestName : List.of(
                "Create FI Payment - Accepted",
                "Create FI Payment - Replay",
                "Create FI Payment - Idempotency Conflict Setup",
                "Create FI Payment - Idempotency Conflict")) {
            assertPacs009AcceptedByRuntimeContract(requestBody(collection, requestName));
        }
    }

    @Test
    void domesticPostmanScenarioBodiesUseKnownParticipantsAndExerciseRequestedSimulatorOutcomes() throws Exception {
        var collection = objectMapper.readTree(Files.readString(COLLECTION, StandardCharsets.UTF_8));

        assertDomesticScenarioOutcome(collection, "Create Payment - Success", "success",
                HkClearingSettlementOutcome.Status.SETTLED);
        assertDomesticScenarioOutcome(collection, "Create Payment - Rejection", "rejection",
                HkClearingSettlementOutcome.Status.REJECTED);
        assertDomesticScenarioOutcome(collection, "Create Payment - Suspicious Proxy Or Account",
                "suspicious_proxy_or_account", HkClearingSettlementOutcome.Status.REJECTED);
        assertDomesticScenarioOutcome(collection, "Create Payment - Pending", "pending",
                HkClearingSettlementOutcome.Status.PENDING);
        assertDomesticScenarioOutcome(collection, "Create Payment - Timeout", "timeout",
                HkClearingSettlementOutcome.Status.TIMEOUT);
        assertDomesticScenarioOutcome(collection, "Create Payment - Internal Failure", "internal_failure",
                HkClearingSettlementOutcome.Status.INTERNAL_FAILURE);
        assertDomesticScenarioOutcome(collection, "Create Payment - Idempotency Conflict Setup", "success",
                HkClearingSettlementOutcome.Status.SETTLED);
        assertDomesticScenarioOutcome(collection, "Create Payment - Idempotency Conflict", "success",
                HkClearingSettlementOutcome.Status.SETTLED);
    }

    @Test
    void fiPostmanRecallBodiesIncludeSupportedReasonAndMatchOriginalReference() throws Exception {
        var collection = objectMapper.readTree(Files.readString(COLLECTION, StandardCharsets.UTF_8));

        for (var requestName : List.of(
                "Create FI Recall - Accepted",
                "Create FI Recall - Rejected",
                "Create FI Recall - Investigation Pending")) {
            assertCamt056AcceptedByRuntimeParser(requestBody(collection, requestName));
        }
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

    @Test
    void rootReadmeActsAsCurrentProductEntryPoint() throws Exception {
        var readme = Files.readString(README, StandardCharsets.UTF_8);

        assertThat(readme).contains("CIB Payment API Simulation Suite");
        assertThat(readme).contains("/v1/domestic-payments", "/v1/fi-payments");
        assertThat(readme).contains("/v1/fi-payments/{paymentId}/recall-requests");
        assertThat(readme).contains("pain.001.001.09", "pain.002.001.10");
        assertThat(readme).contains("pacs.009.001.08", "camt.056.001.08", "camt.029.001.09");
        assertThat(readme).contains("NOSTRO", "VOSTRO", "LORO");
        assertThat(readme).contains("fi-payments:create", "fi-payments:read", "fi-payments:investigate");
        assertThat(readme).contains("docs/developer-support/postman-local-testing.md");
        assertThat(readme).contains("docs/product-strategy/payment-simulation-suite-vision.md");
        assertThat(readme).contains("2026-05-29-add-fi-correspondent-rfi-workflow");
    }

    private void assertDomesticScenarioOutcome(
            JsonNode collection,
            String requestName,
            String scenario,
            HkClearingSettlementOutcome.Status expectedStatus) {
        var candidate = isoPaymentAdmissionService.admit(requestBody(collection, requestName), "application/pain.001+xml");
        assertThat(candidate.debtor().bankCode()).as(requestName + " debtor participant").isEqualTo("CIBBHKHH");
        assertThat(candidate.beneficiary().participantIdentifier())
                .as(requestName + " creditor participant")
                .isEqualTo("SUPPHKHH");

        var transfer = new InternalInterbankTransfer(
                "pacs008-artifact-" + requestName.replace(' ', '-').toLowerCase(),
                new PaymentId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                candidate.debtor(),
                candidate.beneficiary(),
                candidate.amount(),
                candidate.endToEndId(),
                candidate.instructionId(),
                candidate.paymentReference(),
                candidate.debtor().bankCode(),
                candidate.beneficiary().participantIdentifier(),
                new CorrelationId("corr-postman-artifact"));

        var outcome = hkSimulator.process(transfer, authorizationContext(), scenario);
        assertThat(outcome.status()).as(requestName + " simulator outcome").isEqualTo(expectedStatus);
    }

    private Set<String> requestNames(JsonNode node) {
        var names = new HashSet<String>();
        collectNames(node.path("item"), names);
        return names;
    }

    private Set<String> folderNames(JsonNode node) {
        var names = new HashSet<String>();
        collectFolderNames(node.path("item"), names);
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

    private String environmentVariableValue(JsonNode environment, String key) {
        var values = new ArrayList<String>();
        environment.path("values").forEach(value -> {
            if (key.equals(value.path("key").asText())) {
                values.add(value.path("value").asText());
            }
        });
        assertThat(values).as(key + " environment variable").hasSize(1);
        return values.getFirst();
    }

    private void assertPacs009AcceptedByRuntimeContract(String xml) {
        var parsed = pacs009Parser.parse(xml);
        assertThat(parsed.instructingAgentBic()).isNotBlank();
        assertThat(parsed.instructedAgentBic()).isNotBlank();
        assertThat(parsed.currency()).isEqualTo("USD");

        var candidate = fiPaymentAdmissionService.admit(xml, "application/pacs.009+xml");
        assertThat(candidate.instructingParty().bic()).isNotBlank();
        assertThat(candidate.instructedParty().bic()).isNotBlank();
        assertThat(candidate.settlementCurrency()).isEqualTo("USD");

        var settlementContext = routeProfile.derive(
                candidate.instructingParty().bic(),
                candidate.instructedParty().bic(),
                candidate.settlementCurrency());
        assertThat(settlementContext.accountRelationshipRole().name()).isIn("NOSTRO", "VOSTRO", "LORO");
    }

    private void assertCamt056AcceptedByRuntimeParser(String xml) {
        var parsed = camt056Parser.parse(xml);

        assertThat(parsed.originalPaymentReference()).isEqualTo(ORIGINAL_FI_REFERENCE);
        assertThat(parsed.reasonCode()).isIn(SUPPORTED_FI_RECALL_REASONS);
    }

    private JsonNode readOpenApi() throws Exception {
        return yamlMapper.readTree(new ClassPathResource("openapi/domestic-payment-api.yaml").getInputStream());
    }

    private String openApiRequestExample(
            JsonNode openApi,
            String path,
            String method,
            String mediaType,
            String exampleName) {
        var value = openApi
                .path("paths")
                .path(path)
                .path(method)
                .path("requestBody")
                .path("content")
                .path(mediaType)
                .path("examples")
                .path(exampleName)
                .path("value")
                .asText();
        assertThat(value).as("%s %s %s %s example".formatted(method, path, mediaType, exampleName)).isNotBlank();
        return value;
    }

    private String requestBody(JsonNode collection, String requestName) {
        var matchingBodies = new ArrayList<String>();
        collectRequestBodies(collection.path("item"), requestName, matchingBodies);
        assertThat(matchingBodies).as(requestName + " request body").hasSize(1);
        return matchingBodies.getFirst();
    }

    private String requestDescription(JsonNode collection, String requestName) {
        var matchingDescriptions = new ArrayList<String>();
        collectRequestDescriptions(collection.path("item"), requestName, matchingDescriptions);
        assertThat(matchingDescriptions).as(requestName + " description").hasSize(1);
        return matchingDescriptions.getFirst();
    }

    private JsonNode savedJsonExample(JsonNode collection, String exampleName) throws Exception {
        var matchingBodies = new ArrayList<String>();
        collectResponseBodies(collection.path("item"), exampleName, matchingBodies);
        assertThat(matchingBodies).as(exampleName + " saved response body").hasSize(1);
        return objectMapper.readTree(matchingBodies.getFirst());
    }

    private JsonNode request(JsonNode collection, String requestName) {
        var matchingRequests = new ArrayList<JsonNode>();
        collectRequests(collection.path("item"), requestName, matchingRequests);
        assertThat(matchingRequests).as(requestName + " request").hasSize(1);
        return matchingRequests.getFirst();
    }

    private void assertClassicRailCreateRequestsUseDistinctIdempotencyKeys(JsonNode collection) {
        var createRequestNames = List.of(
                "ACH Direct Credit - Settled",
                "ACH Direct Credit - Partially Returned",
                "ACH Direct Credit - Rejected",
                "Corporate RTGS - Settled",
                "FI RTGS - Settled",
                "FI RTGS - Queued For Liquidity",
                "RTGS - Rejected");

        var idempotencyKeyVariables = createRequestNames.stream()
                .map(requestName -> headerValue(request(collection, requestName), "Idempotency-Key"))
                .toList();

        assertThat(idempotencyKeyVariables)
                .as("Classic rail create requests use unique Idempotency-Key variables")
                .doesNotHaveDuplicates();
    }

    private void assertClassicRailCreateRequest(
            JsonNode collection,
            String requestName,
            String endpoint,
            String tokenVariable,
            String idempotencyKeyVariable,
            String scenario,
            String expectedLifecycleStatus) {
        var request = request(collection, requestName);

        assertThat(request.path("method").asText()).as(requestName + " method").isEqualTo("POST");
        assertThat(request.path("url").path("raw").asText()).as(requestName + " URL").contains(endpoint);
        assertThat(headerValue(request, "Authorization")).isEqualTo("Bearer " + tokenVariable);
        assertThat(headerValue(request, "Idempotency-Key")).isEqualTo(idempotencyKeyVariable);
        assertThat(headerValue(request, "X-Correlation-ID")).isEqualTo("{{correlationId}}");
        assertThat(headerValue(request, "X-Mock-Scenario")).isEqualTo(scenario);
        assertThat(headerValue(request, "Content-Type")).isEqualTo("application/json");
        assertThat(headerValue(request, "Accept")).isEqualTo("application/json");
        assertThat(requestDescription(collection, requestName)).contains(
                "Expected HTTP status: 202",
                "expected lifecycle status: " + expectedLifecycleStatus,
                "Common setup");
        assertThat(requestItem(collection, requestName).path("event").toString()).contains(
                "pm.response.to.have.status(202)",
                expectedLifecycleStatus);
    }

    private void assertClassicRailStatusRequest(
            JsonNode collection,
            String requestName,
            String endpoint,
            String tokenVariable) {
        var request = request(collection, requestName);

        assertThat(request.path("method").asText()).as(requestName + " method").isEqualTo("GET");
        assertThat(request.path("url").path("raw").asText()).as(requestName + " URL").contains(endpoint);
        assertThat(headerValue(request, "Authorization")).isEqualTo("Bearer " + tokenVariable);
        assertThat(headerNames(request)).doesNotContain("Idempotency-Key");
        assertThat(headerValue(request, "X-Correlation-ID")).isEqualTo("{{correlationId}}");
        assertThat(headerValue(request, "Accept")).isEqualTo("application/json");
        assertThat(requestDescription(collection, requestName)).contains(
                "Expected HTTP status: 200",
                "Common setup");
        assertThat(requestItem(collection, requestName).path("event").toString())
                .contains("pm.response.to.have.status(200)");
    }

    private String headerValue(JsonNode request, String headerName) {
        var values = new ArrayList<String>();
        request.path("header").forEach(header -> {
            if (headerName.equals(header.path("key").asText())) {
                values.add(header.path("value").asText());
            }
        });
        assertThat(values).as(headerName + " header").hasSize(1);
        return values.getFirst();
    }

    private Set<String> headerNames(JsonNode request) {
        var names = new HashSet<String>();
        request.path("header").forEach(header -> names.add(header.path("key").asText()));
        return names;
    }

    private JsonNode requestItem(JsonNode collection, String requestName) {
        var matchingItems = new ArrayList<JsonNode>();
        collectRequestItems(collection.path("item"), requestName, matchingItems);
        assertThat(matchingItems).as(requestName + " item").hasSize(1);
        return matchingItems.getFirst();
    }

    private void assertFiCorrespondentContext(JsonNode response) {
        var context = response.path("correspondentSettlementContext");

        assertThat(context.path("instructingAgentBic").asText()).isEqualTo("CIBBHKHH");
        assertThat(context.path("instructedAgentBic").asText()).isEqualTo("CORRUS33");
        assertThat(context.path("correspondentOrIntermediaryBic").asText()).isEqualTo("CORRUS33");
        assertThat(context.path("settlementCurrency").asText()).isEqualTo("USD");
        assertThat(context.path("accountRelationshipRole").asText()).isEqualTo("NOSTRO");
        assertThat(context.path("maskedSimulatedAccountReference").asText()).isEqualTo("SIM-USD-NOSTRO-****0001");
    }

    private AuthorizationContext authorizationContext() {
        return new AuthorizationContext(
                "client-a",
                "client-a",
                Set.of("payments:create", "payments:read"),
                null,
                Map.of(),
                Instant.parse("2026-05-24T00:00:00Z"),
                null,
                new CorrelationId("corr-auth"));
    }

    private void collectNames(JsonNode items, Set<String> names) {
        items.forEach(item -> {
            if (item.has("request")) {
                names.add(item.path("name").asText());
            }
            collectNames(item.path("item"), names);
        });
    }

    private void collectFolderNames(JsonNode items, Set<String> names) {
        items.forEach(item -> {
            if (item.has("item") && !item.has("request")) {
                names.add(item.path("name").asText());
            }
            collectFolderNames(item.path("item"), names);
        });
    }

    private void collectResponseNames(JsonNode items, Set<String> names) {
        items.forEach(item -> {
            item.path("response").forEach(response -> names.add(response.path("name").asText()));
            collectResponseNames(item.path("item"), names);
        });
    }

    private void collectRequestDescriptions(JsonNode items, String requestName, List<String> descriptions) {
        items.forEach(item -> {
            if (item.has("request") && requestName.equals(item.path("name").asText())) {
                descriptions.add(item.path("request").path("description").asText());
            }
            collectRequestDescriptions(item.path("item"), requestName, descriptions);
        });
    }

    private void collectRequestBodies(JsonNode items, String requestName, List<String> bodies) {
        items.forEach(item -> {
            if (item.has("request") && requestName.equals(item.path("name").asText())) {
                bodies.add(item.path("request").path("body").path("raw").asText());
            }
            collectRequestBodies(item.path("item"), requestName, bodies);
        });
    }

    private void collectRequests(JsonNode items, String requestName, List<JsonNode> requests) {
        items.forEach(item -> {
            if (item.has("request") && requestName.equals(item.path("name").asText())) {
                requests.add(item.path("request"));
            }
            collectRequests(item.path("item"), requestName, requests);
        });
    }

    private void collectRequestItems(JsonNode items, String requestName, List<JsonNode> matchingItems) {
        items.forEach(item -> {
            if (item.has("request") && requestName.equals(item.path("name").asText())) {
                matchingItems.add(item);
            }
            collectRequestItems(item.path("item"), requestName, matchingItems);
        });
    }

    private void collectResponseBodies(JsonNode items, String responseName, List<String> bodies) {
        items.forEach(item -> {
            item.path("response").forEach(response -> {
                if (responseName.equals(response.path("name").asText())) {
                    bodies.add(response.path("body").asText());
                }
            });
            collectResponseBodies(item.path("item"), responseName, bodies);
        });
    }
}
