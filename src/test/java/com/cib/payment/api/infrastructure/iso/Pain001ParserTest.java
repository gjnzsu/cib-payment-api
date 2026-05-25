package com.cib.payment.api.infrastructure.iso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.ValidationFailureException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Pain001ParserTest {
    private final Pain001Parser parser = new Pain001Parser();

    @Test
    void parsesSupportedPain001BusinessFieldsFromFixture() throws Exception {
        var parsed = parser.parse(readFixture("pain001-success.xml"));

        assertThat(parsed.messageId()).isEqualTo("MSG-20260524-0001");
        assertThat(parsed.paymentInformationId()).isEqualTo("PMT-20260524-SUCCESS");
        assertThat(parsed.debtor().accountName()).isEqualTo("Acme Treasury HK");
        assertThat(parsed.debtor().accountNumber()).isEqualTo("000123456789");
        assertThat(parsed.creditorName()).isEqualTo("Supplier HK Limited");
        assertThat(parsed.creditorAccount()).isEqualTo("000987654321");
        assertThat(parsed.creditorProxyId()).isNull();
        assertThat(parsed.amount().currency()).isEqualTo("HKD");
        assertThat(parsed.amount().value()).isEqualTo("1250.00");
        assertThat(parsed.endToEndId()).isEqualTo("INV-2026-0001");
        assertThat(parsed.instructionId()).isEqualTo("INSTR-20260524-0001");
        assertThat(parsed.remittanceInformation()).isEqualTo("Invoice INV-2026-0001 supplier settlement");
        assertThat(parsed.structuredCreditorReference()).isNull();
        assertThat(parsed.purposeCode()).isEqualTo("SUPP");
        assertThat(parsed.categoryPurposeCode()).isEqualTo("SUPP");
    }

    @Test
    void parsesCreditorProxyAndStructuredReferenceWhenPresent() throws Exception {
        var proxyOnlyXml = proxyOnlyWithStructuredReference();

        var parsed = parser.parse(proxyOnlyXml);

        assertThat(parsed.creditorAccount()).isNull();
        assertThat(parsed.creditorProxyId()).isEqualTo("supplier-proxy@example.test");
        assertThat(parsed.creditorProxyType()).isEqualTo("EMAL");
        assertThat(parsed.structuredCreditorReference()).isEqualTo("SCREF-2026-0001");
        assertThat(parsed.remittanceInformation()).isEqualTo("Invoice INV-2026-0001 supplier settlement");
    }

    @Test
    void parsesCreditorAccountProxyAndParticipantWhenAllArePresent() throws Exception {
        var parsed = parser.parse(readFixture("pain001-suspicious.xml"));

        assertThat(parsed.creditorAccount()).isEqualTo("000987654323");
        assertThat(parsed.creditorProxyId()).isEqualTo("supplier.proxy@example.invalid");
        assertThat(parsed.creditorProxyType()).isEqualTo("EMAL");
        assertThat(parsed.creditorParticipantIdentifier()).isEqualTo("SUPPHKHH");
    }

    @Test
    void parsesMinimalCreditorProxyWithoutType() throws Exception {
        var parsed = parser.parse(minimalProxyOnlyWithStructuredReference());

        assertThat(parsed.creditorAccount()).isNull();
        assertThat(parsed.creditorProxyId()).isEqualTo("proxy@example.test");
        assertThat(parsed.creditorProxyType()).isNull();
        assertThat(parsed.structuredCreditorReference()).isEqualTo("SCREF-2026-0001");
    }

    @Test
    void rejectsMalformedXml() {
        assertThatThrownBy(() -> parser.parse("<Document><CstmrCdtTrfInitn></Document>"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Malformed XML");
    }

    @Test
    void rejectsUnsupportedNamespaceOrMessageVersion() throws Exception {
        var unsupported = readFixture("pain001-success.xml")
                .replace("urn:iso:std:iso:20022:tech:xsd:pain.001.001.09",
                        "urn:iso:std:iso:20022:tech:xsd:pain.001.001.03");

        assertThatThrownBy(() -> parser.parse(unsupported))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsupported pain.001 namespace");
    }

    @Test
    void rejectsMultiplePaymentInformationBlocks() throws Exception {
        var xml = readFixture("pain001-success.xml")
                .replace("</PmtInf>", "</PmtInf><PmtInf><PmtInfId>PMT-SECOND</PmtInfId></PmtInf>");

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("one payment instruction");
    }

    @Test
    void rejectsMultipleCreditTransferTransactions() throws Exception {
        var transaction = """
                <CdtTrfTxInf>
                        <PmtId>
                          <InstrId>INSTR-SECOND</InstrId>
                          <EndToEndId>INV-SECOND</EndToEndId>
                        </PmtId>
                        <Amt>
                          <InstdAmt Ccy="HKD">1.00</InstdAmt>
                        </Amt>
                        <CdtrAgt>
                          <FinInstnId>
                            <BICFI>SUPPHKHH</BICFI>
                          </FinInstnId>
                        </CdtrAgt>
                        <Cdtr>
                          <Nm>Second Supplier</Nm>
                        </Cdtr>
                        <CdtrAcct>
                          <Id>
                            <Othr>
                              <Id>000000000002</Id>
                            </Othr>
                          </Id>
                        </CdtrAcct>
                      </CdtTrfTxInf>
                """;
        var xml = readFixture("pain001-success.xml")
                .replace("</CdtTrfTxInf>", "</CdtTrfTxInf>" + transaction);

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("one payment instruction");
    }

    @Test
    void rejectsDoctypeAndExternalEntityXml() {
        var unsafeXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Document [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pain.001.001.09">
                  <CstmrCdtTrfInitn>&xxe;</CstmrCdtTrfInitn>
                </Document>
                """;

        assertThatThrownBy(() -> parser.parse(unsafeXml))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsafe XML");
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
