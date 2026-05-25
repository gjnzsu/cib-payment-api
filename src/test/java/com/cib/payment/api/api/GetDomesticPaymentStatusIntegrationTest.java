package com.cib.payment.api.api;

import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

import com.cib.payment.api.testsupport.JwtTestSupport;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.xml.sax.InputSource;

@SpringBootTest
@AutoConfigureMockMvc
@Import(GetDomesticPaymentStatusIntegrationTest.JwtTestConfiguration.class)
class GetDomesticPaymentStatusIntegrationTest {
    private static final String PAIN_002_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pain.002.001.10";
    private static final Map<String, String> NS = Map.of("iso", PAIN_002_NAMESPACE);

    private final MockMvc mockMvc;

    @Autowired
    GetDomesticPaymentStatusIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void createThenGetReturnsLatestEnginePain002Status() throws Exception {
        var paymentId = createIsoPayment("client-a", "status-key-1", "rejection");

        mockMvc.perform(get("/v1/domestic-payments/{paymentId}", paymentId)
                        .header("Authorization", bearer("client-a", "payments:read"))
                        .accept("application/pain.002+xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/pain.002+xml"))
                .andExpect(xpath("//iso:AcctSvcrRef", NS).string(paymentId))
                .andExpect(xpath("//iso:TxSts", NS).string("RJCT"));
    }

    @Test
    void unknownUuidReturnsNotFoundJsonError() throws Exception {
        mockMvc.perform(get("/v1/domestic-payments/550e8400-e29b-41d4-a716-446655440000")
                        .header("Authorization", bearer("client-a", "payments:read")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("PAYMENT_NOT_FOUND")));
    }

    @Test
    void malformedUuidReturnsBadRequestJsonError() throws Exception {
        mockMvc.perform(get("/v1/domestic-payments/not-a-uuid")
                        .header("Authorization", bearer("client-a", "payments:read")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    void crossClientLookupReturnsNotFoundJsonError() throws Exception {
        var paymentId = createIsoPayment("client-a", "status-key-2", "success");

        mockMvc.perform(get("/v1/domestic-payments/{paymentId}", paymentId)
                        .header("Authorization", bearer("client-b", "payments:read")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", equalTo("PAYMENT_NOT_FOUND")));
    }

    @Test
    void getDoesNotRequireIdempotencyKey() throws Exception {
        var paymentId = createIsoPayment("client-a", "status-key-3", "success");

        mockMvc.perform(get("/v1/domestic-payments/{paymentId}", paymentId)
                        .header("Authorization", bearer("client-a", "payments:read"))
                        .accept("application/pain.002+xml"))
                .andExpect(status().isOk())
                .andExpect(xpath("//iso:TxSts", NS).string("ACSC"));
    }

    private String createIsoPayment(String clientId, String idempotencyKey, String scenario) throws Exception {
        var responseXml = mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer(clientId, "payments:create"))
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Mock-Scenario", scenario)
                        .contentType("application/xml")
                        .accept("application/pain.002+xml")
                        .content(fixture("iso/pain001-success.xml")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return text(responseXml, "//*[local-name()='AcctSvcrRef']");
    }

    private String fixture(String path) throws Exception {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }

    private String text(String xml, String expression) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        var xpath = XPathFactory.newInstance().newXPath();
        return (String) xpath.evaluate(expression, document, XPathConstants.STRING);
    }

    private String bearer(String clientId, String... scopes) {
        return "Bearer " + JwtTestSupport.tokenWithScopes(clientId, scopes);
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
