package com.cib.payment.api.infrastructure.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.application.port.HkClearingSettlementOutcome;
import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.BeneficiaryIdentifier;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.InternalInterbankTransfer;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentId;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeterministicHkClearingSettlementSimulatorTest {
    private final DeterministicHkClearingSettlementSimulator simulator =
            new DeterministicHkClearingSettlementSimulator();

    @Test
    void settlesKnownParticipantsForSuccessScenario() {
        var outcome = simulator.process(transfer("HKD", "CIBBHKHH", "SUPPHKHH"), authorizationContext(), "success");

        assertThat(outcome.status()).isEqualTo(HkClearingSettlementOutcome.Status.SETTLED);
        assertThat(outcome.reason()).isEmpty();
    }

    @Test
    void rejectsUnknownPayerOrPayeeParticipantsBeforeScenarioOutcome() {
        var unknownPayer = simulator.process(transfer("HKD", "UNKNOWNHK", "SUPPHKHH"), authorizationContext(), "success");
        var unknownPayee = simulator.process(transfer("HKD", "CIBBHKHH", "UNKNOWNHK"), authorizationContext(), "success");

        assertThat(unknownPayer.status()).isEqualTo(HkClearingSettlementOutcome.Status.REJECTED);
        assertThat(unknownPayer.reason()).hasValueSatisfying(reason ->
                assertThat(reason.code()).isEqualTo("HK_UNKNOWN_PARTICIPANT"));
        assertThat(unknownPayee.status()).isEqualTo(HkClearingSettlementOutcome.Status.REJECTED);
        assertThat(unknownPayee.reason()).hasValueSatisfying(reason ->
                assertThat(reason.code()).isEqualTo("HK_UNKNOWN_PARTICIPANT"));
    }

    @Test
    void rejectsNonHkdTransfersEvenIfAdmissionWouldNormallyBlockThem() {
        var outcome = simulator.process(transfer("USD", "CIBBHKHH", "SUPPHKHH"), authorizationContext(), "success");

        assertThat(outcome.status()).isEqualTo(HkClearingSettlementOutcome.Status.REJECTED);
        assertThat(outcome.reason()).hasValueSatisfying(reason ->
                assertThat(reason.code()).isEqualTo("HK_UNSUPPORTED_CURRENCY"));
    }

    @Test
    void supportsDeterministicLocalScenarios() {
        assertThat(simulator.process(transfer("HKD", "CIBBHKHH", "SUPPHKHH"), authorizationContext(), "rejection").status())
                .isEqualTo(HkClearingSettlementOutcome.Status.REJECTED);
        assertThat(simulator.process(
                        transfer("HKD", "CIBBHKHH", "SUPPHKHH"),
                        authorizationContext(),
                        "suspicious_proxy_or_account")
                .reason())
                .hasValueSatisfying(reason -> assertThat(reason.code()).isEqualTo("HK_SUSPICIOUS_PROXY_OR_ACCOUNT"));
        assertThat(simulator.process(transfer("HKD", "CIBBHKHH", "SUPPHKHH"), authorizationContext(), "pending").status())
                .isEqualTo(HkClearingSettlementOutcome.Status.PENDING);
        assertThat(simulator.process(transfer("HKD", "CIBBHKHH", "SUPPHKHH"), authorizationContext(), "timeout").status())
                .isEqualTo(HkClearingSettlementOutcome.Status.TIMEOUT);
        assertThat(simulator.process(
                        transfer("HKD", "CIBBHKHH", "SUPPHKHH"),
                        authorizationContext(),
                        "internal_failure")
                .status())
                .isEqualTo(HkClearingSettlementOutcome.Status.INTERNAL_FAILURE);
    }

    private InternalInterbankTransfer transfer(String currency, String payerParticipant, String payeeParticipant) {
        return new InternalInterbankTransfer(
                "pacs008-11111111",
                new PaymentId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                new AccountReference(payerParticipant, "000123456789", "Acme Treasury HK"),
                BeneficiaryIdentifier.account("000987654321", "Supplier HK Limited", payeeParticipant),
                new Money(currency, "1250.00"),
                "INV-2026-0001",
                "INSTR-20260524-0001",
                "PMT-20260524-SUCCESS",
                payerParticipant,
                payeeParticipant,
                new CorrelationId("corr-simulator-test"));
    }

    private AuthorizationContext authorizationContext() {
        return new AuthorizationContext(
                "client-a",
                "client-a",
                Set.of("payments:create", "payments:read"),
                "tenant-a",
                Map.of("actor", "treasury-system"),
                Instant.parse("2026-05-24T00:00:00Z"),
                "jwt-001",
                new CorrelationId("corr-auth"));
    }
}
