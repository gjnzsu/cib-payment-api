package com.cib.payment.api.infrastructure.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.application.port.PaymentEngineInitiationPort;
import com.cib.payment.api.application.port.PaymentEngineRecordRepository;
import com.cib.payment.api.application.port.PaymentEngineStatusQueryPort;
import com.cib.payment.api.application.service.IsoPaymentAdmissionService;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.EnginePaymentRecord;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentStatus;
import com.cib.payment.api.infrastructure.iso.Pain001Parser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HkPaymentEngineTest {
    private static final Instant FIXED_NOW = Instant.parse("2026-05-24T10:15:30Z");
    private static final UUID FIXED_PAYMENT_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final PaymentEngineRecordRepository repository = new InMemoryPaymentEngineRecordRepository();
    private final HkPaymentEngine engine = new HkPaymentEngine(
            repository,
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
        assertThat(record.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(record.correlationId()).isEqualTo(correlationId);
        assertThat(record.createdAt()).isEqualTo(FIXED_NOW);
        assertThat(record.updatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    void keepsLatestStatusReportEmptyUntilPain002GenerationExists() throws Exception {
        var record = engine.initiate(
                admissionService.admit(readFixture("pain001-success.xml"), "application/xml"),
                authorizationContext("client-a"),
                new CorrelationId("corr-engine-status-placeholder"),
                "idem-002",
                "success");

        assertThat(record.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(record.latestStatusReportXml()).isEmpty();
        assertThat(record.internalInterbankTransfer()).isEmpty();
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
    void edgeAdmissionServiceDoesNotDependOnEngineRecordRepository() {
        assertThat(IsoPaymentAdmissionService.class.getDeclaredFields())
                .noneMatch(field -> field.getType().equals(PaymentEngineRecordRepository.class));
        assertThat(IsoPaymentAdmissionService.class.getDeclaredConstructors())
                .allSatisfy(constructor -> assertThat(constructor.getParameterTypes())
                        .doesNotContain(PaymentEngineRecordRepository.class));
        assertThat(engine).isInstanceOf(PaymentEngineInitiationPort.class);
        assertThat(engine).isInstanceOf(PaymentEngineStatusQueryPort.class);
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
