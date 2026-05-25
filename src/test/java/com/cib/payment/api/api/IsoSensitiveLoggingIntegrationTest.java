package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.payment.api.infrastructure.observability.AccountNumberMasker;
import com.cib.payment.api.testsupport.JwtTestSupport;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "debug=false",
        "logging.level.root=INFO",
        "logging.level.org.springframework=INFO",
        "logging.level.org.springframework.web=INFO"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(IsoSensitiveLoggingIntegrationTest.JwtTestConfiguration.class)
class IsoSensitiveLoggingIntegrationTest {
    private final MockMvc mockMvc;

    @Autowired
    IsoSensitiveLoggingIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void maskerRedactsIsoAccountProxyAndHkidLikeIdentifiers() {
        assertThat(AccountNumberMasker.maskSensitive("000123456789")).isEqualTo("********6789");
        assertThat(AccountNumberMasker.maskSensitive("merchant@example.hk")).isEqualTo("m******t@example.hk");
        assertThat(AccountNumberMasker.maskSensitive("+85291234567")).isEqualTo("*******4567");
        assertThat(AccountNumberMasker.maskSensitive("A123456(7)")).isEqualTo("A******(*)");
        assertThat(AccountNumberMasker.maskSensitive("FPS-merchant@example.hk")).isEqualTo("F***************e.hk");
    }

    @Test
    void isoPaymentFlowLogsMaskedIdentifiersWithoutRawXmlOrBearerToken(CapturedOutput output) throws Exception {
        var rawAccount = "987654321012";
        var rawProxy = "merchant@example.hk";
        var rawMobile = "+85291234567";
        var rawHkid = "A123456(7)";
        var xml = fixture("iso/pain001-suspicious.xml")
                .replace("000987654323", rawAccount)
                .replace("supplier.proxy@example.invalid", rawProxy)
                .replace("Invoice INV-2026-0003 suspicious simulator scenario", "Pay proxy " + rawMobile + " / " + rawHkid);

        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", bearer("payments:create"))
                        .header("Idempotency-Key", "iso-sensitive-logging")
                        .header("X-Correlation-ID", "corr-iso-sensitive")
                        .header("X-Mock-Scenario", "suspicious_proxy_or_account")
                        .contentType("application/pain.001+xml")
                        .accept("application/pain.002+xml")
                        .content(xml))
                .andExpect(status().isOk());

        assertThat(output).contains("iso_payment_initiation_admitted");
        assertThat(output).contains("engine_payment_mapped");
        assertThat(output).contains("hk_simulator_outcome");
        assertThat(output).contains("pain002_generated");
        assertThat(output).contains("correlationId=corr-iso-sensitive");
        assertThat(output).contains("creditorAccount=********1012");
        assertThat(output).contains("proxy=m******t@example.hk");
        assertThat(output).contains("reasonCode=HK_SUSPICIOUS_PROXY_OR_ACCOUNT");

        assertThat(output).doesNotContain("<Document");
        assertThat(output).doesNotContain("Bearer ");
        assertThat(output).doesNotContain(rawAccount);
        assertThat(output).doesNotContain(rawProxy);
        assertThat(output).doesNotContain(rawMobile);
        assertThat(output).doesNotContain(rawHkid);
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
