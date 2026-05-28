package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

import com.cib.payment.api.testsupport.JwtTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(FiPaymentControllerIntegrationTest.JwtTestConfiguration.class)
class FiPaymentControllerIntegrationTest {
    private static final String CAMT_029_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:camt.029.001.09";
    private static final Map<String, String> CAMT_NS = Map.of("iso", CAMT_029_NAMESPACE);

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    @Autowired
    FiPaymentControllerIntegrationTest(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    @Test
    void acceptedFiPaymentCreateAndStatusReturnJsonWithCorrelationAndDerivedContext() throws Exception {
        var create = createFiPayment(
                "fi-client-a",
                "fi-api-accepted",
                "corr-fi-api-accepted",
                "fi_payment_accepted",
                "pacs009-accepted-nostro.xml");
        var paymentId = create.get("paymentId").asText();

        assertThat(create.get("status").asText()).isEqualTo("SETTLED");
        assertThat(create.get("correlationId").asText()).isEqualTo("corr-fi-api-accepted");
        assertThat(create.at("/correspondentSettlementContext/accountRelationshipRole").asText()).isEqualTo("NOSTRO");
        assertThat(create.at("/correspondentSettlementContext/maskedSimulatedAccountReference").asText())
                .isEqualTo("nostro-usd-corrus33-****1234");
        assertThat(create.get("statusLink").asText()).isEqualTo("/v1/fi-payments/" + paymentId);

        mockMvc.perform(get("/v1/fi-payments/{paymentId}", paymentId)
                        .header("Authorization", bearer("fi-client-a", "fi-payments:read"))
                        .header("X-Correlation-ID", "corr-fi-api-status")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-fi-api-status"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.paymentId", equalTo(paymentId)))
                .andExpect(jsonPath("$.status", equalTo("SETTLED")))
                .andExpect(jsonPath("$.messageId", equalTo("FI-MSG-20260528-ACCEPTED-NOSTRO")))
                .andExpect(jsonPath("$.originalPaymentReference", equalTo("FI-E2E-20260528-0001")))
                .andExpect(jsonPath("$.correspondentSettlementContext.accountRelationshipRole", equalTo("NOSTRO")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-fi-api-accepted")))
                .andExpect(jsonPath("$.links.self", equalTo("/v1/fi-payments/" + paymentId)));
    }

    @ParameterizedTest
    @MethodSource("fiPaymentOutcomes")
    void fiPaymentCreateReturnsExpectedStatusAndCorrespondentContext(
            String scenario,
            String fixture,
            String expectedStatus,
            String expectedRole,
            String expectedReasonCode) throws Exception {
        var result = mockMvc.perform(post("/v1/fi-payments")
                        .header("Authorization", bearer("fi-client-a", "fi-payments:create"))
                        .header("Idempotency-Key", "fi-api-outcome-" + scenario)
                        .header("X-Correlation-ID", "corr-fi-api-outcome-" + expectedRole.toLowerCase())
                        .header("X-Mock-Scenario", scenario)
                        .contentType("application/pacs.009+xml")
                        .accept(MediaType.APPLICATION_JSON)
                        .content(fixture(fixture)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.paymentId", notNullValue()))
                .andExpect(jsonPath("$.status", equalTo(expectedStatus)))
                .andExpect(jsonPath("$.correspondentSettlementContext.accountRelationshipRole", equalTo(expectedRole)));
        if (expectedReasonCode == null) {
            result.andExpect(jsonPath("$.reason", nullValue()));
        } else {
            result.andExpect(jsonPath("$.reason.code", equalTo(expectedReasonCode)));
        }
    }

    @Test
    void duplicateFiCreateWithEquivalentBusinessXmlReplaysOriginalJsonResponse() throws Exception {
        var first = mockMvc.perform(post("/v1/fi-payments")
                        .header("Authorization", bearer("fi-client-a", "fi-payments:create"))
                        .header("Idempotency-Key", "fi-api-create-replay")
                        .header("X-Correlation-ID", "corr-fi-api-create-original")
                        .header("X-Mock-Scenario", "fi_payment_accepted")
                        .contentType("application/pacs.009+xml")
                        .accept(MediaType.APPLICATION_JSON)
                        .content(fixture("pacs009-accepted-nostro.xml")))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var replay = mockMvc.perform(post("/v1/fi-payments")
                        .header("Authorization", bearer("fi-client-a", "fi-payments:create"))
                        .header("Idempotency-Key", "fi-api-create-replay")
                        .header("X-Correlation-ID", "corr-fi-api-create-replay")
                        .header("X-Mock-Scenario", "fi_payment_accepted")
                        .contentType("text/xml")
                        .accept(MediaType.APPLICATION_JSON)
                        .content(fixture("pacs009-accepted-nostro.xml").replaceAll(">\\s+<", "><")))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-ID", "corr-fi-api-create-replay"))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-fi-api-create-original")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(replay).isEqualTo(first);
    }

    @Test
    void duplicateFiCreateWithDifferentScenarioReturnsConflictJsonError() throws Exception {
        mockMvc.perform(post("/v1/fi-payments")
                        .header("Authorization", bearer("fi-client-a", "fi-payments:create"))
                        .header("Idempotency-Key", "fi-api-create-conflict")
                        .header("X-Mock-Scenario", "fi_payment_accepted")
                        .contentType("application/pacs.009+xml")
                        .content(fixture("pacs009-accepted-nostro.xml")))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/v1/fi-payments")
                        .header("Authorization", bearer("fi-client-a", "fi-payments:create"))
                        .header("Idempotency-Key", "fi-api-create-conflict")
                        .header("X-Correlation-ID", "corr-fi-api-create-conflict")
                        .header("X-Mock-Scenario", "fi_payment_pending_correspondent_review")
                        .contentType("application/pacs.009+xml")
                        .accept(MediaType.APPLICATION_JSON)
                        .content(fixture("pacs009-accepted-nostro.xml")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_CONFLICT")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-fi-api-create-conflict")));
    }

    @Test
    void recallAcceptedReturnsCamt029XmlAndStatusIncludesInvestigationSummary() throws Exception {
        var paymentId = createFiPayment(
                        "fi-client-a",
                        "fi-api-recall-accepted-payment",
                        "corr-fi-api-recall-payment",
                        "fi_payment_accepted",
                        "pacs009-accepted-nostro.xml")
                .get("paymentId")
                .asText();

        mockMvc.perform(post("/v1/fi-payments/{paymentId}/recall-requests", paymentId)
                        .header("Authorization", bearer("fi-client-a", "fi-payments:investigate"))
                        .header("Idempotency-Key", "fi-api-recall-accepted")
                        .header("X-Correlation-ID", "corr-fi-api-recall-accepted")
                        .header("X-Mock-Scenario", "recall_accepted")
                        .contentType("application/camt.056+xml")
                        .accept("application/camt.029+xml")
                        .content(fixture("camt056-recall-accepted.xml")))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-ID", "corr-fi-api-recall-accepted"))
                .andExpect(content().contentTypeCompatibleWith("application/camt.029+xml"))
                .andExpect(xpath("//iso:Conf", CAMT_NS).string("CNCL"))
                .andExpect(xpath("//iso:CorrelationId", CAMT_NS).string("corr-fi-api-recall-accepted"))
                .andExpect(xpath("//iso:FiPaymentId", CAMT_NS).string(paymentId));

        mockMvc.perform(get("/v1/fi-payments/{paymentId}", paymentId)
                        .header("Authorization", bearer("fi-client-a", "fi-payments:read"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recallInvestigation.status", equalTo("ACCEPTED")))
                .andExpect(jsonPath("$.recallInvestigation.originalPaymentReference", equalTo("FI-E2E-20260528-0001")));
    }

    @ParameterizedTest
    @MethodSource("recallOutcomes")
    void recallOutcomesReturnExpectedCamt029Resolution(
            String paymentFixture,
            String paymentScenario,
            String recallFixture,
            String recallScenario,
            String expectedConfirmation,
            String expectedReasonCode) throws Exception {
        var paymentId = createFiPayment(
                        "fi-client-a",
                        "fi-api-recall-outcome-" + recallScenario,
                        "corr-fi-api-recall-outcome-payment-" + recallScenario,
                        paymentScenario,
                        paymentFixture)
                .get("paymentId")
                .asText();

        mockMvc.perform(post("/v1/fi-payments/{paymentId}/recall-requests", paymentId)
                        .header("Authorization", bearer("fi-client-a", "fi-payments:investigate"))
                        .header("Idempotency-Key", "fi-api-recall-outcome-key-" + recallScenario)
                        .header("X-Correlation-ID", "corr-fi-api-recall-outcome-" + recallScenario)
                        .header("X-Mock-Scenario", recallScenario)
                        .contentType("application/camt.056+xml")
                        .accept("application/camt.029+xml")
                        .content(fixture(recallFixture)))
                .andExpect(status().isAccepted())
                .andExpect(content().contentTypeCompatibleWith("application/camt.029+xml"))
                .andExpect(xpath("//iso:Conf", CAMT_NS).string(expectedConfirmation))
                .andExpect(xpath("//iso:CxlStsRsnInf/iso:Rsn/iso:Cd", CAMT_NS).string(expectedReasonCode));
    }

    @Test
    void duplicateRecallReplaysOriginalCamt029AndSecondRecallConflictReturnsJson() throws Exception {
        var paymentId = createFiPayment(
                        "fi-client-a",
                        "fi-api-duplicate-recall-payment",
                        "corr-fi-api-duplicate-recall-payment",
                        "fi_payment_accepted",
                        "pacs009-accepted-nostro.xml")
                .get("paymentId")
                .asText();

        var first = mockMvc.perform(post("/v1/fi-payments/{paymentId}/recall-requests", paymentId)
                        .header("Authorization", bearer("fi-client-a", "fi-payments:investigate"))
                        .header("Idempotency-Key", "fi-api-duplicate-recall")
                        .header("X-Correlation-ID", "corr-fi-api-recall-original")
                        .header("X-Mock-Scenario", "recall_accepted")
                        .contentType("application/camt.056+xml")
                        .accept("application/camt.029+xml")
                        .content(fixture("camt056-recall-accepted.xml")))
                .andExpect(status().isAccepted())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var replay = mockMvc.perform(post("/v1/fi-payments/{paymentId}/recall-requests", paymentId)
                        .header("Authorization", bearer("fi-client-a", "fi-payments:investigate"))
                        .header("Idempotency-Key", "fi-api-duplicate-recall")
                        .header("X-Correlation-ID", "corr-fi-api-recall-replay")
                        .header("X-Mock-Scenario", "recall_accepted")
                        .contentType("application/xml")
                        .accept("application/camt.029+xml")
                        .content(fixture("camt056-recall-accepted.xml").replaceAll(">\\s+<", "><")))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-ID", "corr-fi-api-recall-replay"))
                .andExpect(xpath("//iso:CorrelationId", CAMT_NS).string("corr-fi-api-recall-original"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(replay).isEqualTo(first);

        mockMvc.perform(post("/v1/fi-payments/{paymentId}/recall-requests", paymentId)
                        .header("Authorization", bearer("fi-client-a", "fi-payments:investigate"))
                        .header("Idempotency-Key", "fi-api-second-recall-conflict")
                        .header("X-Correlation-ID", "corr-fi-api-second-recall-conflict")
                        .header("X-Mock-Scenario", "recall_rejected")
                        .contentType("application/camt.056+xml")
                        .accept(MediaType.APPLICATION_JSON)
                        .content(fixture("camt056-recall-rejected.xml")))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_CONFLICT")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-fi-api-second-recall-conflict")));
    }

    @Test
    void recallValidationFailuresReturnJsonErrorEnvelopesWithCorrelation() throws Exception {
        var acceptedPaymentId = createFiPayment(
                        "fi-client-a",
                        "fi-api-recall-wrong-ref-payment",
                        "corr-fi-api-recall-wrong-ref-payment",
                        "fi_payment_accepted",
                        "pacs009-accepted-nostro.xml")
                .get("paymentId")
                .asText();
        mockMvc.perform(post("/v1/fi-payments/{paymentId}/recall-requests", acceptedPaymentId)
                        .header("Authorization", bearer("fi-client-a", "fi-payments:investigate"))
                        .header("Idempotency-Key", "fi-api-recall-wrong-ref")
                        .header("X-Correlation-ID", "corr-fi-api-recall-wrong-ref")
                        .header("X-Mock-Scenario", "recall_accepted")
                        .contentType("application/camt.056+xml")
                        .content(fixture("camt056-wrong-original-reference.xml")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-fi-api-recall-wrong-ref")));

        var rejectedPaymentId = createFiPayment(
                        "fi-client-a",
                        "fi-api-recall-rejected-payment",
                        "corr-fi-api-recall-rejected-payment",
                        "fi_payment_rejected_unsupported_correspondent",
                        "pacs009-rejected-loro.xml")
                .get("paymentId")
                .asText();
        mockMvc.perform(post("/v1/fi-payments/{paymentId}/recall-requests", rejectedPaymentId)
                        .header("Authorization", bearer("fi-client-a", "fi-payments:investigate"))
                        .header("Idempotency-Key", "fi-api-recall-rejected-payment")
                        .header("X-Correlation-ID", "corr-fi-api-recall-rejected-payment")
                        .header("X-Mock-Scenario", "recall_rejected")
                        .contentType("application/camt.056+xml")
                        .content(fixture("camt056-recall-rejected.xml")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-fi-api-recall-rejected-payment")));
    }

    @Test
    void invalidXmlMissingIdempotencyAndNotFoundReturnJsonErrorEnvelopesWithCorrelation() throws Exception {
        mockMvc.perform(post("/v1/fi-payments")
                        .header("Authorization", bearer("fi-client-a", "fi-payments:create"))
                        .header("X-Correlation-ID", "corr-fi-api-missing-idem")
                        .header("X-Mock-Scenario", "fi_payment_accepted")
                        .contentType("application/pacs.009+xml")
                        .accept(MediaType.APPLICATION_JSON)
                        .content(fixture("pacs009-accepted-nostro.xml")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-fi-api-missing-idem")));

        mockMvc.perform(post("/v1/fi-payments")
                        .header("Authorization", bearer("fi-client-a", "fi-payments:create"))
                        .header("Idempotency-Key", "fi-api-unsafe-xml")
                        .header("X-Correlation-ID", "corr-fi-api-unsafe-xml")
                        .header("X-Mock-Scenario", "fi_payment_accepted")
                        .contentType("application/pacs.009+xml")
                        .accept(MediaType.APPLICATION_JSON)
                        .content(fixture("pacs009-unsafe.xml")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-fi-api-unsafe-xml")));

        mockMvc.perform(get("/v1/fi-payments/550e8400-e29b-41d4-a716-446655440000")
                        .header("Authorization", bearer("fi-client-a", "fi-payments:read"))
                        .header("X-Correlation-ID", "corr-fi-api-not-found")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("PAYMENT_NOT_FOUND")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-fi-api-not-found")));
    }

    @Test
    void authScopeAndOwnershipFailuresReturnJsonWithoutLeakingForeignPaymentData() throws Exception {
        mockMvc.perform(post("/v1/fi-payments")
                        .header("X-Correlation-ID", "corr-fi-api-missing-auth")
                        .contentType("application/pacs.009+xml")
                        .content(fixture("pacs009-accepted-nostro.xml")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-ID", "corr-fi-api-missing-auth"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("UNAUTHORIZED")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-fi-api-missing-auth")));

        mockMvc.perform(post("/v1/fi-payments")
                        .header("Authorization", bearer("fi-client-a", "payments:create"))
                        .header("X-Correlation-ID", "corr-fi-api-wrong-scope")
                        .contentType("application/pacs.009+xml")
                        .content(fixture("pacs009-accepted-nostro.xml")))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("FORBIDDEN")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-fi-api-wrong-scope")));

        var paymentId = createFiPayment(
                        "fi-client-a",
                        "fi-api-foreign-owner-payment",
                        "corr-fi-api-foreign-owner-payment",
                        "fi_payment_accepted",
                        "pacs009-accepted-nostro.xml")
                .get("paymentId")
                .asText();

        mockMvc.perform(get("/v1/fi-payments/{paymentId}", paymentId)
                        .header("Authorization", bearer("fi-client-b", "fi-payments:read"))
                        .header("X-Correlation-ID", "corr-fi-api-foreign-status")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PAYMENT_NOT_FOUND")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-fi-api-foreign-status")));

        mockMvc.perform(post("/v1/fi-payments/{paymentId}/recall-requests", paymentId)
                        .header("Authorization", bearer("fi-client-b", "fi-payments:investigate"))
                        .header("Idempotency-Key", "fi-api-foreign-recall")
                        .header("X-Correlation-ID", "corr-fi-api-foreign-recall")
                        .header("X-Mock-Scenario", "recall_accepted")
                        .contentType("application/camt.056+xml")
                        .accept(MediaType.APPLICATION_JSON)
                        .content(fixture("camt056-recall-accepted.xml")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PAYMENT_NOT_FOUND")))
                .andExpect(jsonPath("$.message", equalTo("Payment was not found")))
                .andExpect(jsonPath("$.correlationId", equalTo("corr-fi-api-foreign-recall")));
    }

    @Test
    void generatedCorrelationIdAppearsInResponseHeaderAndJsonBodyWhenHeaderIsAbsent() throws Exception {
        mockMvc.perform(post("/v1/fi-payments")
                        .header("Authorization", bearer("fi-client-a", "fi-payments:create"))
                        .header("Idempotency-Key", "fi-api-generated-correlation")
                        .header("X-Mock-Scenario", "fi_payment_accepted")
                        .contentType("application/pacs.009+xml")
                        .accept(MediaType.APPLICATION_JSON)
                        .content(fixture("pacs009-accepted-nostro.xml")))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-ID", startsWith("")))
                .andExpect(jsonPath("$.correlationId", notNullValue()));
    }

    private JsonNode createFiPayment(
            String clientId,
            String idempotencyKey,
            String correlationId,
            String scenario,
            String fixture) throws Exception {
        var response = mockMvc.perform(post("/v1/fi-payments")
                        .header("Authorization", bearer(clientId, "fi-payments:create"))
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Correlation-ID", correlationId)
                        .header("X-Mock-Scenario", scenario)
                        .contentType("application/pacs.009+xml")
                        .accept(MediaType.APPLICATION_JSON)
                        .content(fixture(fixture)))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-ID", correlationId))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private static Stream<Arguments> fiPaymentOutcomes() {
        return Stream.of(
                Arguments.of("fi_payment_accepted", "pacs009-accepted-nostro.xml", "SETTLED", "NOSTRO", null),
                Arguments.of(
                        "fi_payment_rejected_unsupported_correspondent",
                        "pacs009-rejected-loro.xml",
                        "REJECTED",
                        "LORO",
                        "FI_UNSUPPORTED_CORRESPONDENT"),
                Arguments.of(
                        "fi_payment_pending_correspondent_review",
                        "pacs009-pending-vostro.xml",
                        "PROCESSING",
                        "VOSTRO",
                        "FI_CORRESPONDENT_REVIEW"));
    }

    private static Stream<Arguments> recallOutcomes() {
        return Stream.of(
                Arguments.of(
                        "pacs009-rejected-loro.xml",
                        "fi_payment_accepted",
                        "camt056-recall-rejected.xml",
                        "recall_rejected",
                        "RJCR",
                        "NOAS"),
                Arguments.of(
                        "pacs009-pending-vostro.xml",
                        "fi_payment_pending_correspondent_review",
                        "camt056-investigation-pending.xml",
                        "investigation_pending",
                        "PDCR",
                        "IPAY"));
    }

    private String fixture(String fileName) throws Exception {
        return new ClassPathResource("fi/" + fileName).getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearer(String clientId, String... scopes) {
        return "Bearer " + JwtTestSupport.fiTokenWithScopes(clientId, scopes);
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
