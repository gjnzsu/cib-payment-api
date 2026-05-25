package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

import com.cib.payment.api.testsupport.JwtTestSupport;
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
@Import(CreateIsoDomesticPaymentIntegrationTest.JwtTestConfiguration.class)
class CreateIsoDomesticPaymentIntegrationTest {
    private static final String PAIN_002_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pain.002.001.10";
    private static final Map<String, String> NS = Map.of("iso", PAIN_002_NAMESPACE);

    private final MockMvc mockMvc;

    @Autowired
    CreateIsoDomesticPaymentIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @ParameterizedTest
    @MethodSource("xmlContentTypes")
    void supportedPain001XmlReturnsPain002Acsc(String contentType) throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-create-acsc-" + contentType.replace('/', '-'))
                        .header("X-Correlation-ID", "corr-iso-create-1")
                        .contentType(contentType)
                        .accept("application/pain.002+xml")
                        .content(fixture("iso/pain001-success.xml")))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "corr-iso-create-1"))
                .andExpect(content().contentTypeCompatibleWith("application/pain.002+xml"))
                .andExpect(xpath("//iso:TxSts", NS).string("ACSC"))
                .andExpect(xpath("//iso:OrgnlMsgNmId", NS).string("pain.001.001.09"));
    }

    @ParameterizedTest
    @MethodSource("processedOutcomes")
    void processedPaymentOutcomesReturnExpectedPain002Status(String scenario, String fixture, String expectedStatus) throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-outcome-" + scenario)
                        .header("X-Mock-Scenario", scenario)
                        .contentType("application/pain.001+xml")
                        .accept("application/pain.002+xml")
                        .content(fixture(fixture)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/pain.002+xml"))
                .andExpect(xpath("//iso:TxSts", NS).string(expectedStatus));
    }

    @Test
    void customJsonInitiationIsRejectedWithJsonError() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-json-rejected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount":{"currency":"HKD","value":"1250.00"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    void malformedXmlReturnsJsonValidationError() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-malformed")
                        .contentType("application/xml")
                        .accept("application/pain.002+xml")
                        .content("<Document><broken></Document>"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    void unsupportedCurrencyReturnsJsonSemanticError() throws Exception {
        var nonHkdXml = fixture("iso/pain001-success.xml").replace("Ccy=\"HKD\">1250.00", "Ccy=\"USD\">1250.00");

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-non-hkd")
                        .contentType("application/xml")
                        .accept("application/pain.002+xml")
                        .content(nonHkdXml))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("SEMANTIC_PAYMENT_ERROR")));
    }

    @Test
    void missingBeneficiaryIdentifierReturnsJsonSemanticError() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-missing-beneficiary")
                        .contentType("application/xml")
                        .accept("application/pain.002+xml")
                        .content(fixture("iso/pain001-invalid-missing-creditor.xml")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("SEMANTIC_PAYMENT_ERROR")));
    }

    @Test
    void missingTraceabilityIdentifierReturnsJsonSemanticError() throws Exception {
        var missingReferenceXml = fixture("iso/pain001-success.xml")
                .replace("<EndToEndId>INV-2026-0001</EndToEndId>", "");

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-missing-traceability")
                        .contentType("application/xml")
                        .accept("application/pain.002+xml")
                        .content(missingReferenceXml))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("SEMANTIC_PAYMENT_ERROR")));
    }

    @Test
    void missingIdempotencyKeyReturnsJsonValidationError() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .contentType("application/xml")
                        .accept("application/pain.002+xml")
                        .content(fixture("iso/pain001-success.xml")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    void semanticallyEquivalentXmlReplayReturnsOriginalPain002() throws Exception {
        var originalXml = fixture("iso/pain001-success.xml");
        var first = mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-replay-equivalent")
                        .header("X-Mock-Scenario", "success")
                        .contentType("application/xml")
                        .accept("application/pain.002+xml")
                        .content(originalXml))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var reformattedXml = originalXml.replaceAll(">\\s+<", "><");
        var replay = mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-replay-equivalent")
                        .header("X-Mock-Scenario", "success")
                        .contentType("text/xml")
                        .accept("application/pain.002+xml")
                        .content(reformattedXml))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(replay).isEqualTo(first);
    }

    @Test
    void sameIdempotencyKeyWithChangedPaymentSemanticsReturnsConflict() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-conflict-semantics")
                        .contentType("application/xml")
                        .accept("application/pain.002+xml")
                        .content(fixture("iso/pain001-success.xml")))
                .andExpect(status().isOk());

        var changedAmountXml = fixture("iso/pain001-success.xml")
                .replace("1250.00", "1251.00");
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-conflict-semantics")
                        .contentType("application/xml")
                        .accept("application/pain.002+xml")
                        .content(changedAmountXml))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_CONFLICT")));
    }

    @Test
    void sameIdempotencyKeyWithChangedScenarioReturnsConflict() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-conflict-scenario")
                        .header("X-Mock-Scenario", "success")
                        .contentType("application/xml")
                        .accept("application/pain.002+xml")
                        .content(fixture("iso/pain001-success.xml")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-conflict-scenario")
                        .header("X-Mock-Scenario", "rejection")
                        .contentType("application/xml")
                        .accept("application/pain.002+xml")
                        .content(fixture("iso/pain001-success.xml")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_CONFLICT")));
    }

    private static Stream<String> xmlContentTypes() {
        return Stream.of("application/xml", "text/xml", "application/pain.001+xml");
    }

    private static Stream<Arguments> processedOutcomes() {
        return Stream.of(
                Arguments.of("rejection", "iso/pain001-rejection.xml", "RJCT"),
                Arguments.of("suspicious_proxy_or_account", "iso/pain001-suspicious.xml", "RJCT"),
                Arguments.of("internal_failure", "iso/pain001-success.xml", "RJCT"),
                Arguments.of("pending", "iso/pain001-pending.xml", "PDNG"),
                Arguments.of("timeout", "iso/pain001-timeout.xml", "PDNG"));
    }

    private String fixture(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private String bearer(String... scopes) {
        return "Bearer " + JwtTestSupport.tokenWithScopes("client-a", scopes);
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
