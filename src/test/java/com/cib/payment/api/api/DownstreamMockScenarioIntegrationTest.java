package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath;

import com.cib.payment.api.testsupport.JwtTestSupport;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
@Import(DownstreamMockScenarioIntegrationTest.JwtTestConfiguration.class)
class DownstreamMockScenarioIntegrationTest {
    private static final String PAIN_002_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pain.002.001.10";
    private static final Map<String, String> NS = Map.of("iso", PAIN_002_NAMESPACE);

    private final MockMvc mockMvc;

    @Autowired
    DownstreamMockScenarioIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @ParameterizedTest
    @CsvSource({
            "success,iso/pain001-success.xml,ACSC",
            "rejection,iso/pain001-rejection.xml,RJCT",
            "suspicious_proxy_or_account,iso/pain001-suspicious.xml,RJCT",
            "pending,iso/pain001-pending.xml,PDNG",
            "timeout,iso/pain001-timeout.xml,PDNG",
            "internal_failure,iso/pain001-success.xml,RJCT"
    })
    void mockScenarioHeaderDrivesIsoStatusOutcome(
            String scenario,
            String fixturePath,
            String expectedStatus) throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "scenario-key-" + scenario)
                        .header("X-Mock-Scenario", scenario)
                        .contentType("application/xml")
                        .accept("application/pain.002+xml")
                        .content(fixture(fixturePath)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/pain.002+xml"))
                .andExpect(xpath("//iso:TxSts", NS).string(expectedStatus));
    }

    @Test
    void invalidIsoFixtureReturnsValidationError() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                .header("Idempotency-Key", "scenario-invalid-key")
                        .contentType("application/xml")
                        .content(fixture("iso/pain001-invalid-missing-creditor.xml")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code", equalTo("SEMANTIC_PAYMENT_ERROR")));
    }

    @Test
    void isoIdempotencyFixturesCoverReplayAndConflict() throws Exception {
        var first = mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "scenario-idempotency-key")
                        .contentType("application/xml")
                        .accept("application/pain.002+xml")
                        .content(fixture("iso/pain001-success.xml")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "scenario-idempotency-key")
                        .contentType("text/xml")
                        .accept("application/pain.002+xml")
                        .content(fixture("iso/pain001-success.xml").replaceAll(">\\s+<", "><")))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(first));

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "scenario-idempotency-key")
                        .contentType("application/xml")
                        .content(fixture("iso/pain001-rejection.xml")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", equalTo("IDEMPOTENCY_CONFLICT")));
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
