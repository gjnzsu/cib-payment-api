package com.cib.payment.api.infrastructure.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.application.service.IsoPaymentAdmissionService;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.EnginePaymentRecord;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentStatus;
import com.cib.payment.api.infrastructure.iso.Pain001Parser;
import com.cib.payment.api.infrastructure.simulator.DeterministicHkClearingSettlementSimulator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.RecordComponent;
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

class HkPaymentEngineMappingTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-05-24T10:15:30Z");
    private static final UUID FIXED_PAYMENT_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final InMemoryPaymentEngineRecordRepository repository = new InMemoryPaymentEngineRecordRepository();
    private final HkPaymentEngine engine = new HkPaymentEngine(
            repository,
            new DeterministicHkClearingSettlementSimulator(),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
            () -> FIXED_PAYMENT_UUID);
    private final IsoPaymentAdmissionService admissionService = new IsoPaymentAdmissionService(new Pain001Parser());

    @Test
    void mapsAdmittedPain001CandidateToInternalInterbankTransferForSimulator() throws Exception {
        var candidate = admissionService.admit(readFixture("pain001-success.xml"), "application/xml");
        var correlationId = new CorrelationId("corr-engine-map");

        EnginePaymentRecord record = engine.initiate(
                candidate,
                authorizationContext("client-a"),
                correlationId,
                "idem-map-001",
                "success");

        assertThat(record.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(record.internalInterbankTransfer()).hasValueSatisfying(transfer -> {
            assertThat(transfer.internalMessageId()).isEqualTo("pacs008-11111111-1111-1111-1111-111111111111");
            assertThat(transfer.paymentId()).isEqualTo(new PaymentId(FIXED_PAYMENT_UUID));
            assertThat(transfer.endToEndId()).isEqualTo("INV-2026-0001");
            assertThat(transfer.instructionId()).isEqualTo("INSTR-20260524-0001");
            assertThat(transfer.paymentReference()).isNull();
            assertThat(transfer.debtor()).isEqualTo(candidate.debtor());
            assertThat(transfer.beneficiary()).isEqualTo(candidate.beneficiary());
            assertThat(transfer.amount()).isEqualTo(candidate.amount());
            assertThat(transfer.payerParticipantIdentifier()).isEqualTo("CIBBHKHH");
            assertThat(transfer.payeeParticipantIdentifier()).isEqualTo("SUPPHKHH");
            assertThat(transfer.correlationId()).isEqualTo(correlationId);
        });
        assertThat(record.latestStatusReportXml()).isEmpty();
    }

    @Test
    void preservesProxyBeneficiaryParticipantRoutingForSuspiciousScenario() throws Exception {
        var candidate = admissionService.admit(readFixture("pain001-suspicious.xml"), "application/xml");

        var record = engine.initiate(
                candidate,
                authorizationContext("client-a"),
                new CorrelationId("corr-engine-proxy"),
                "idem-proxy-001",
                "suspicious_proxy_or_account");

        assertThat(record.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(record.statusReason()).hasValueSatisfying(reason ->
                assertThat(reason.code()).isEqualTo("HK_SUSPICIOUS_PROXY_OR_ACCOUNT"));
        assertThat(record.internalInterbankTransfer()).hasValueSatisfying(transfer -> {
            assertThat(transfer.beneficiary().accountNumber()).contains("000987654323");
            assertThat(transfer.beneficiary().fpsProxyType()).isEqualTo("EMAL");
            assertThat(transfer.beneficiary().fpsProxyValue()).contains("supplier.proxy@example.invalid");
            assertThat(transfer.payeeParticipantIdentifier()).isEqualTo("SUPPHKHH");
        });
    }

    @Test
    void doesNotPersistLocalScenarioContextOnEnginePaymentRecord() {
        assertThat(EnginePaymentRecord.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("scenarioContext");
    }

    @ParameterizedTest
    @MethodSource("scenarioStatuses")
    void mapsSimulatorOutcomesToEngineStatuses(String scenario, PaymentStatus expectedStatus, String expectedReasonCode)
            throws Exception {
        var record = engine.initiate(
                admissionService.admit(readFixture("pain001-success.xml"), "application/xml"),
                authorizationContext("client-a"),
                new CorrelationId("corr-engine-" + scenario),
                "idem-" + scenario,
                scenario);

        assertThat(record.status()).isEqualTo(expectedStatus);
        if (expectedReasonCode == null) {
            assertThat(record.statusReason()).isEmpty();
        } else {
            assertThat(record.statusReason()).hasValueSatisfying(reason ->
                    assertThat(reason.code()).isEqualTo(expectedReasonCode));
        }
    }

    @Test
    void statusQueryReturnsMappedRecordOnlyForOwningClient() throws Exception {
        var owner = authorizationContext("client-a");
        var record = engine.initiate(
                admissionService.admit(readFixture("pain001-success.xml"), "application/xml"),
                owner,
                new CorrelationId("corr-engine-query"),
                "idem-query-001",
                "pending");

        assertThat(engine.findByPaymentId(record.paymentId(), owner))
                .contains(record)
                .get()
                .extracting(EnginePaymentRecord::status)
                .isEqualTo(PaymentStatus.PROCESSING);
        assertThat(engine.findByPaymentId(record.paymentId(), authorizationContext("client-b"))).isEmpty();
    }

    private static Stream<Arguments> scenarioStatuses() {
        return Stream.of(
                Arguments.of("success", PaymentStatus.COMPLETED, null),
                Arguments.of("rejection", PaymentStatus.REJECTED, "HK_CLEARING_REJECTION"),
                Arguments.of("suspicious_proxy_or_account", PaymentStatus.REJECTED, "HK_SUSPICIOUS_PROXY_OR_ACCOUNT"),
                Arguments.of("pending", PaymentStatus.PROCESSING, "HK_PENDING_PROCESSING"),
                Arguments.of("timeout", PaymentStatus.TIMEOUT, "HK_SIMULATOR_TIMEOUT"),
                Arguments.of("internal_failure", PaymentStatus.FAILED, "HK_SIMULATOR_INTERNAL_FAILURE"));
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
