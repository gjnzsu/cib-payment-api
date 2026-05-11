package com.cib.payment.api.infrastructure.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cib.payment.api.testsupport.JwtTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(SecurityIntegrationTest.JwtTestConfiguration.class)
class SecurityIntegrationTest {
    private final MockMvc mockMvc;

    @Autowired
    SecurityIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    @Test
    void missingTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("X-Correlation-ID", "security-corr-401")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Correlation-ID", "security-corr-401"))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Authentication is required or invalid"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.correlationId").value("security-corr-401"));
    }

    @Test
    void expiredTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", "Bearer " + JwtTestSupport.expiredToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongAudienceReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithWrongAudience())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingSubjectClaimReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenMissingClaim("sub"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidSignatureReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithInvalidSignature())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingCreateScopeReturnsForbiddenOnPost() throws Exception {
        mockMvc.perform(post("/v1/domestic-payments")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithScopes("client-a", "payments:read"))
                        .header("X-Correlation-ID", "security-corr-403")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Correlation-ID", "security-corr-403"))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Authenticated client lacks the required scope"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.correlationId").value("security-corr-403"));
    }

    @Test
    void missingReadScopeReturnsForbiddenOnGet() throws Exception {
        mockMvc.perform(get("/v1/domestic-payments/550e8400-e29b-41d4-a716-446655440000")
                        .header("Authorization", "Bearer " + JwtTestSupport.tokenWithScopes("client-a", "payments:create")))
                .andExpect(status().isForbidden());
    }

    static class JwtTestConfiguration {
        @Bean
        JwtDecoder jwtDecoder() {
            return JwtTestSupport.jwtDecoder();
        }
    }
}
