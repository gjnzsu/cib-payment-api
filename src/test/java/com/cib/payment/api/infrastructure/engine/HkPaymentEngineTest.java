package com.cib.payment.api.infrastructure.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.application.port.HkClearingSettlementOutcome;
import com.cib.payment.api.application.port.HkClearingSettlementSimulator;
import com.cib.payment.api.application.port.PaymentEngineInitiationPort;
import com.cib.payment.api.application.port.PaymentEngineRecordRepository;
import com.cib.payment.api.application.port.PaymentEngineStatusQueryPort;
import com.cib.payment.api.application.service.IsoPaymentAdmissionService;
import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.BeneficiaryIdentifier;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.EnginePaymentRecord;
import com.cib.payment.api.domain.model.IsoPaymentCandidate;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentStatus;
import com.cib.payment.api.infrastructure.iso.Pain001Parser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HkPaymentEngineTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-05-24T10:15:30Z");
    private static final UUID FIXED_PAYMENT_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final PaymentEngineRecordRepository repository = new InMemoryPaymentEngineRecordRepository();
    private final HkPaymentEngine engine = new HkPaymentEngine(
            repository,
            settledSimulator(),
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
            () -> FIXED_PAYMENT_UUID);
    private final IsoPaymentAdmissionService admissionService = new IsoPaymentAdmissionService(new Pain001Parser());

    @Test
    void createsEngineOwnedPaymentRecordForAdmittedCandidate() throws Exception {
        var candidate = admissionService.admit(readFixture("pain001-success.xml"), "application/xml");
        var authorizationContext = authorizationContext("client-a");
        var correlationId = new CorrelationId("corr-engine-create");

        EnginePaymentRecord record = engine.initiate(candidate, authorizationContext, correlationId, "idem-001", "success");

        assertThat(record.paymentId()).isEqualTo(new PaymentId(FIXED_PAYMENT_UUID));
        assertThat(record.clientId()).isEqualTo("client-a");
        assertThat(record.candidate()).isEqualTo(candidate);
        assertThat(record.candidate().messageId()).isEqualTo("MSG-20260524-0001");
        assertThat(record.candidate().endToEndId()).isEqualTo("INV-2026-0001");
        assertThat(record.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(record.correlationId()).isEqualTo(correlationId);
        assertThat(record.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(record.updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void storesLatestStatusReportXmlAfterPain002Generation() throws Exception {
        var record = engine.initiate(
                admissionService.admit(readFixture("pain001-success.xml"), "application/xml"),
                authorizationContext("client-a"),
                new CorrelationId("corr-engine-status-placeholder"),
                "idem-002",
                "success");

        assertThat(record.status()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(record.latestStatusReportXml()).hasValueSatisfying(xml ->
                assertThat(xml).contains("<TxSts>ACSC</TxSts>"));
        assertThat(record.internalInterbankTransfer()).isPresent();
    }

    @Test
    void statusQueryReturnsEngineOwnedRecordForOwningClient() throws Exception {
        var authorizationContext = authorizationContext("client-a");
        var record = engine.initiate(
                admissionService.admit(readFixture("pain001-success.xml"), "application/xml"),
                authorizationContext,
                new CorrelationId("corr-engine-query"),
                "idem-003",
                "success");

        assertThat(((PaymentEngineStatusQueryPort) engine).findByPaymentId(record.paymentId(), authorizationContext))
                .contains(record);
    }

    @Test
    void statusQueryDoesNotExposeUnknownOrForeignClientRecords() throws Exception {
        var owningClient = authorizationContext("client-a");
        var foreignClient = authorizationContext("client-b");
        var record = engine.initiate(
                admissionService.admit(readFixture("pain001-success.xml"), "application/xml"),
                owningClient,
                new CorrelationId("corr-engine-foreign"),
                "idem-004",
                "success");

        assertThat(engine.findByPaymentId(record.paymentId(), foreignClient)).isEmpty();
        assertThat(engine.findByPaymentId(new PaymentId(UUID.randomUUID()), owningClient)).isEmpty();
    }

    @Test
    void admissionReturnsCandidateAndEngineOwnsRecordCreation() throws Exception {
        var candidate = admissionService.admit(readFixture("pain001-success.xml"), "application/xml");
        var authorizationContext = authorizationContext("client-a");
        var paymentId = new PaymentId(FIXED_PAYMENT_UUID);

        assertThat(repository.findByPaymentIdAndClientId(paymentId, authorizationContext.clientId())).isEmpty();

        var record = engine.initiate(
                candidate,
                authorizationContext,
                new CorrelationId("corr-engine-owner"),
                "idem-owner",
                "success");

        assertThat(repository.findByPaymentIdAndClientId(paymentId, authorizationContext.clientId())).contains(record);
        assertThat(engine).isInstanceOf(PaymentEngineInitiationPort.class);
        assertThat(engine).isInstanceOf(PaymentEngineStatusQueryPort.class);
    }

    @Test
    void usesInjectedClearingSettlementSimulatorOutcome() throws Exception {
        var authorizationContext = authorizationContext("client-a");
        var capturedAuthorizationContext = new AuthorizationContext[1];
        var timeoutEngine = new HkPaymentEngine(
                new InMemoryPaymentEngineRecordRepository(),
                (transfer, simulatorAuthorizationContext, scenarioContext) -> {
                    capturedAuthorizationContext[0] = simulatorAuthorizationContext;
                    return new HkClearingSettlementOutcome(
                        HkClearingSettlementOutcome.Status.TIMEOUT,
                        new PaymentReason("TEST_TIMEOUT", "Injected simulator timeout"));
                },
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
                () -> FIXED_PAYMENT_UUID);

        var record = timeoutEngine.initiate(
                admissionService.admit(readFixture("pain001-success.xml"), "application/xml"),
                authorizationContext,
                new CorrelationId("corr-injected-simulator"),
                "idem-injected",
                "success");

        assertThat(record.status()).isEqualTo(PaymentStatus.TIMEOUT);
        assertThat(record.statusReason()).hasValueSatisfying(reason ->
                assertThat(reason.code()).isEqualTo("TEST_TIMEOUT"));
        assertThat(capturedAuthorizationContext[0]).isEqualTo(authorizationContext);
    }

    @Test
    void enginePaymentRecordNormalizesNullOptionalComponents() {
        var record = new EnginePaymentRecord(
                new PaymentId(FIXED_PAYMENT_UUID),
                "client-a",
                candidate(),
                PaymentStatus.PROCESSING,
                FIXED_NOW,
                FIXED_NOW,
                new CorrelationId("corr-null-optionals"),
                null,
                null,
                null,
                "idem-null-optionals");

        assertThat(record.internalInterbankTransfer()).isEmpty();
        assertThat(record.latestStatusReportXml()).isEmpty();
        assertThat(record.statusReason()).isEmpty();
    }

    @Test
    void beneficiaryIdentifierNormalizesNullOptionalComponents() {
        var beneficiary = new BeneficiaryIdentifier(
                null,
                "Supplier HK Limited",
                "SUPPHKHH",
                "EMAL",
                null);

        assertThat(beneficiary.accountNumber()).isEmpty();
        assertThat(beneficiary.fpsProxyValue()).isEmpty();
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

    private HkClearingSettlementSimulator settledSimulator() {
        return (transfer, authorizationContext, scenarioContext) -> new HkClearingSettlementOutcome(
                HkClearingSettlementOutcome.Status.SETTLED,
                Optional.empty());
    }

    private IsoPaymentCandidate candidate() {
        return new IsoPaymentCandidate(
                new AccountReference("CIBBHKHH", "000123456789", "Acme Treasury HK"),
                BeneficiaryIdentifier.account("000987654321", "Supplier HK Limited", "SUPPHKHH"),
                new Money("HKD", "1250.00"),
                "INV-2026-0001",
                "INSTR-20260524-0001",
                null,
                "Invoice INV-2026-0001 supplier settlement",
                "SUPP",
                "SUPP",
                "MSG-20260524-0001",
                "PMT-20260524-SUCCESS",
                "pain.001.001.09");
    }
}
