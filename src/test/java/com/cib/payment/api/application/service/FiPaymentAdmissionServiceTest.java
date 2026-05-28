package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.Pacs009FiPaymentParser;
import com.cib.payment.api.infrastructure.iso.Pacs009Parser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FiPaymentAdmissionServiceTest {
    private final Pacs009FiPaymentParser parser = new Pacs009Parser();
    private final FiPaymentAdmissionService admissionService = new FiPaymentAdmissionService(parser);

    @Test
    void admitsValidUsdFiPaymentCandidate() throws Exception {
        var candidate = admissionService.admit(readFixture("pacs009-accepted-nostro.xml"), "application/pacs.009+xml");

        assertThat(candidate.identifiers().messageId()).isEqualTo("FI-MSG-20260528-ACCEPTED-NOSTRO");
        assertThat(candidate.identifiers().instructionId()).isEqualTo("FICLIENT01-PACS009-ACCEPTED-NOSTRO");
        assertThat(candidate.identifiers().originalPaymentReference()).isEqualTo("FI-E2E-20260528-0001");
        assertThat(candidate.instructingParty().bic()).isEqualTo("CIBBHKHH");
        assertThat(candidate.instructedParty().bic()).isEqualTo("CORRUS33");
        assertThat(candidate.intermediaryBic()).isEmpty();
        assertThat(candidate.settlementAmount().currency()).isEqualTo("USD");
        assertThat(candidate.settlementAmount().value()).isEqualTo("100000.00");
        assertThat(candidate.settlementCurrency()).isEqualTo("USD");
        assertThat(candidate.settlementDate()).hasToString("2026-05-28");
        assertThat(candidate.sourceMessageType()).isEqualTo("pacs.009.001.08");
    }

    @Test
    void rejectsMissingRequiredFiPartyField() throws Exception {
        assertThatThrownBy(() -> admissionService.admit(
                        readFixture("pacs009-missing-instructed-agent.xml"), "application/xml"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Instructed agent BIC is required");
    }

    @Test
    void rejectsNonUsdSettlementCurrency() throws Exception {
        assertThatThrownBy(() -> admissionService.admit(readFixture("pacs009-non-usd.xml"), "text/xml"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Only USD FI settlement currency is supported");
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        assertThatThrownBy(() -> admissionService.admit(readFixture("pacs009-accepted-nostro.xml"), "application/json"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("requires supported pacs.009 XML");
    }

    @Test
    void rejectsBlankBody() {
        assertThatThrownBy(() -> admissionService.admit("   ", "application/xml"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("FI payment XML body is required");
    }

    private String readFixture(String fileName) throws Exception {
        return Files.readString(Path.of("src", "test", "resources", "fi", fileName), StandardCharsets.UTF_8);
    }
}
