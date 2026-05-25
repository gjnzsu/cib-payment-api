package com.cib.payment.api.infrastructure.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.service.IsoPaymentAdmissionService;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentStatus;
import com.cib.payment.api.infrastructure.iso.Pain001Parser;
import com.cib.payment.api.infrastructure.iso.Pain002Renderer;
import com.cib.payment.api.infrastructure.simulator.DeterministicHkClearingSettlementSimulator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HkPaymentEngineStatusReportTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-05-24T10:15:30Z");
    private static final UUID FIXED_PAYMENT_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final InMemoryPaymentEngineRecordRepository repository = new InMemoryPaymentEngineRecordRepository();
    private final HkPaymentEngine engine = new HkPaymentEngine(
            repository,
            new DeterministicHkClearingSettlementSimulator(),
            new Pain002Renderer(),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
            () -> FIXED_PAYMENT_UUID);
    private final IsoPaymentAdmissionService admissionService = new IsoPaymentAdmissionService(new Pain001Parser());

    @ParameterizedTest
    @MethodSource("scenarioReports")
    void storesLatestPain002StatusReportAfterSimulatorProcessing(
            String scenario,
            PaymentStatus expectedInternalStatus,
            String expectedIsoStatus,
            String expectedReasonCode)
            throws Exception {
        var candidate = admissionService.admit(readFixture("pain001-success.xml"), "application/xml");
        var authorizationContext = authorizationContext("client-a");

        var record = engine.initiate(
                candidate,
                authorizationContext,
                new CorrelationId("corr-engine-report-" + scenario),
                "idem-report-" + scenario,
                scenario);

        assertThat(record.status()).isEqualTo(expectedInternalStatus);
        assertThat(record.latestStatusReportXml()).hasValueSatisfying(xml -> {
            assertThat(xml).contains("<TxSts>" + expectedIsoStatus + "</TxSts>");
            assertThat(xml).contains("<OrgnlMsgId>MSG-20260524-0001</OrgnlMsgId>");
            assertThat(xml).contains("<OrgnlPmtInfId>PMT-20260524-SUCCESS</OrgnlPmtInfId>");
            assertThat(xml).contains("<OrgnlInstrId>INSTR-20260524-0001</OrgnlInstrId>");
            assertThat(xml).contains("<OrgnlEndToEndId>INV-2026-0001</OrgnlEndToEndId>");
            assertThat(xml).contains("<AcctSvcrRef>11111111-1111-1111-1111-111111111111</AcctSvcrRef>");
            if (expectedReasonCode == null) {
                assertThat(xml).doesNotContain("<StsRsnInf>");
            } else {
                assertThat(xml).contains("<Cd>" + expectedReasonCode + "</Cd>");
            }
        });
        assertThat(repository.findByPaymentIdAndClientId(record.paymentId(), authorizationContext.clientId()))
                .contains(record);
    }

    @Test
    void admissionFailureDoesNotCreateEnginePain002StatusReport() throws Exception {
        var paymentId = new PaymentId(FIXED_PAYMENT_UUID);
        var authorizationContext = authorizationContext("client-a");

        assertThatThrownBy(() -> admissionService.admit(
                readFixture("pain001-invalid-missing-creditor.xml"),
                "application/xml"))
                .isInstanceOf(ValidationFailureException.class);

        assertThat(repository.findByPaymentIdAndClientId(paymentId, authorizationContext.clientId()))
                .isEmpty();
    }

    private static Stream<Arguments> scenarioReports() {
        return Stream.of(
                Arguments.of("success", PaymentStatus.COMPLETED, "ACSC", null),
                Arguments.of("rejection", PaymentStatus.REJECTED, "RJCT", "AC01"),
                Arguments.of("pending", PaymentStatus.PROCESSING, "PDNG", "SL01"),
                Arguments.of("timeout", PaymentStatus.TIMEOUT, "PDNG", "NARR"),
                Arguments.of("internal_failure", PaymentStatus.FAILED, "RJCT", "MS03"));
    }

    private AuthorizationContext authorizationContext(String clientId) {
        return new AuthorizationContext(
                clientId,
                clientId,
                Set.of("payments:create", "payments:read"),
                null,
                Map.of(),
                Instant.parse("2026-05-24T00:00:00Z"),
                null,
                new CorrelationId("corr-auth"));
    }

    private String readFixture(String fileName) throws Exception {
        return Files.readString(Path.of("src", "test", "resources", "iso", fileName), StandardCharsets.UTF_8);
    }
}
