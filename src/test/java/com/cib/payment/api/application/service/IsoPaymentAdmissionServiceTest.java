package com.cib.payment.api.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.SemanticPaymentException;
import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.Pain001PaymentInitiationParser;
import com.cib.payment.api.infrastructure.iso.Pain001Parser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IsoPaymentAdmissionServiceTest {
    private final Pain001PaymentInitiationParser parser = new Pain001Parser();
    private final IsoPaymentAdmissionService admissionService = new IsoPaymentAdmissionService(parser);

    @Test
    void admitsValidPain001Candidate() throws Exception {
        var candidate = admissionService.admit(readFixture("pain001-success.xml"), "application/xml");

        assertThat(candidate.messageId()).isEqualTo("MSG-20260524-0001");
        assertThat(candidate.debtor().accountNumber()).isEqualTo("000123456789");
        assertThat(candidate.beneficiary().accountNumber()).contains("000987654321");
        assertThat(candidate.beneficiary().fpsProxyValue()).isEmpty();
        assertThat(candidate.amount().currency()).isEqualTo("HKD");
        assertThat(candidate.amount().value()).isEqualTo("1250.00");
        assertThat(candidate.endToEndId()).isEqualTo("INV-2026-0001");
        assertThat(candidate.paymentReference()).isNull();
        assertThat(candidate.remittanceInformation()).isEqualTo("Invoice INV-2026-0001 supplier settlement");
        assertThat(candidate.purposeCode()).isEqualTo("SUPP");
        assertThat(candidate.categoryPurposeCode()).isEqualTo("SUPP");
    }

    @Test
    void admitsProxyOnlyBeneficiaryCandidate() throws Exception {
        var candidate = admissionService.admit(proxyOnlyWithStructuredReference(), "application/xml");

        assertThat(candidate.beneficiary().accountNumber()).isEmpty();
        assertThat(candidate.beneficiary().fpsProxyType()).isEqualTo("EMAL");
        assertThat(candidate.beneficiary().fpsProxyValue()).contains("supplier-proxy@example.test");
        assertThat(candidate.paymentReference()).isEqualTo("SCREF-2026-0001");
    }

    @Test
    void admitsMinimalProxyOnlyBeneficiaryWithoutType() throws Exception {
        var candidate = admissionService.admit(minimalProxyOnlyWithStructuredReference(), "application/xml");

        assertThat(candidate.beneficiary().accountNumber()).isEmpty();
        assertThat(candidate.beneficiary().fpsProxyType()).isNull();
        assertThat(candidate.beneficiary().fpsProxyValue()).contains("proxy@example.test");
        assertThat(candidate.paymentReference()).isEqualTo("SCREF-2026-0001");
    }

    @Test
    void rejectsCustomJsonContentType() throws Exception {
        assertThatThrownBy(() -> admissionService.admit(readFixture("pain001-success.xml"), "application/json"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("requires supported pain.001 XML");
    }

    @Test
    void rejectsMissingBeneficiaryAccountOrProxy() throws Exception {
        assertThatThrownBy(() -> admissionService.admit(readFixture("pain001-invalid-missing-creditor.xml"), "application/xml"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Beneficiary account or FPS proxy is required");
    }

    @Test
    void rejectsMissingEndToEndIdOrReference() throws Exception {
        var missingReference = readFixture("pain001-success.xml")
                .replace("<EndToEndId>INV-2026-0001</EndToEndId>", "");

        assertThatThrownBy(() -> admissionService.admit(missingReference, "application/xml"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("EndToEndId or payment reference is required");
    }

    @Test
    void admitsStructuredCreditorReferenceWhenEndToEndIdIsMissing() throws Exception {
        var structuredReferenceOnly = proxyOnlyWithStructuredReference()
                .replace("<EndToEndId>INV-2026-0001</EndToEndId>", "");

        var candidate = admissionService.admit(structuredReferenceOnly, "application/xml");

        assertThat(candidate.endToEndId()).isNull();
        assertThat(candidate.paymentReference()).isEqualTo("SCREF-2026-0001");
        assertThat(candidate.remittanceInformation()).isEqualTo("Invoice INV-2026-0001 supplier settlement");
    }

    @Test
    void rejectsNonHkdCurrencyAsProfileSemanticFailure() throws Exception {
        var nonHkd = readFixture("pain001-success.xml")
                .replace("Ccy=\"HKD\">1250.00", "Ccy=\"USD\">1250.00");

        assertThatThrownBy(() -> admissionService.admit(nonHkd, "application/xml"))
                .isInstanceOf(SemanticPaymentException.class)
                .hasMessageContaining("Only HKD payments are supported");
    }

    private String readFixture(String fileName) throws Exception {
        return Files.readString(Path.of("src", "test", "resources", "iso", fileName), StandardCharsets.UTF_8);
    }

    private String proxyOnlyWithStructuredReference() throws Exception {
        return readFixture("pain001-success.xml")
                .replace("""
                        <CdtrAcct>
                                  <Id>
                                    <Othr>
                                      <Id>000987654321</Id>
                                    </Othr>
                                  </Id>
                                  <Ccy>HKD</Ccy>
                                </CdtrAcct>
                        """, """
                        <CdtrAcct>
                                  <Prxy>
                                    <Tp>
                                      <Cd>EMAL</Cd>
                                    </Tp>
                                    <Id>supplier-proxy@example.test</Id>
                                  </Prxy>
                                  <Ccy>HKD</Ccy>
                                </CdtrAcct>
                        """)
                .replace("""
                        <RmtInf>
                                  <Ustrd>Invoice INV-2026-0001 supplier settlement</Ustrd>
                                </RmtInf>
                        """, """
                        <RmtInf>
                                  <Ustrd>Invoice INV-2026-0001 supplier settlement</Ustrd>
                                  <Strd>
                                    <CdtrRefInf>
                                      <Ref>SCREF-2026-0001</Ref>
                                    </CdtrRefInf>
                                  </Strd>
                                </RmtInf>
                        """);
    }

    private String minimalProxyOnlyWithStructuredReference() throws Exception {
        return proxyOnlyWithStructuredReference()
                .replace("""
                        <Prxy>
                                    <Tp>
                                      <Cd>EMAL</Cd>
                                    </Tp>
                                    <Id>supplier-proxy@example.test</Id>
                                  </Prxy>
                        """, """
                        <Prxy>
                                    <Id>proxy@example.test</Id>
                                  </Prxy>
                        """);
    }
}
