package com.cib.payment.api.infrastructure.iso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.ValidationFailureException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Pacs009ParserTest {
    private final Pacs009Parser parser = new Pacs009Parser();

    @Test
    void parsesSupportedPacs009BusinessFieldsFromFixture() throws Exception {
        var parsed = parser.parse(readFixture("pacs009-accepted-nostro.xml"));

        assertThat(parsed.messageId()).isEqualTo("FI-MSG-20260528-ACCEPTED-NOSTRO");
        assertThat(parsed.instructionId()).isEqualTo("FICLIENT01-PACS009-ACCEPTED-NOSTRO");
        assertThat(parsed.endToEndId()).isEqualTo("FI-E2E-20260528-0001");
        assertThat(parsed.amount()).isEqualTo("100000.00");
        assertThat(parsed.currency()).isEqualTo("USD");
        assertThat(parsed.settlementDate()).isEqualTo("2026-05-28");
        assertThat(parsed.instructingAgentBic()).isEqualTo("CIBBHKHH");
        assertThat(parsed.instructedAgentBic()).isEqualTo("CORRUS33");
        assertThat(parsed.intermediaryAgentBic()).isNull();
        assertThat(parsed.sourceMessageType()).isEqualTo("pacs.009.001.08");
    }

    @Test
    void parsesNonUsdCurrencyForAdmissionValidation() throws Exception {
        var parsed = parser.parse(readFixture("pacs009-non-usd.xml"));

        assertThat(parsed.currency()).isEqualTo("EUR");
        assertThat(parsed.amount()).isEqualTo("100000.00");
    }

    @Test
    void rejectsMalformedXml() {
        assertThatThrownBy(() -> parser.parse("<Document><FICdtTrf></Document>"))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Malformed XML");
    }

    @Test
    void rejectsDoctypeAndExternalEntityXml() throws Exception {
        assertThatThrownBy(() -> parser.parse(readFixture("pacs009-unsafe.xml")))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsafe XML");
    }

    @Test
    void rejectsUnsupportedNamespaceOrMessageVersion() throws Exception {
        var unsupported = readFixture("pacs009-accepted-nostro.xml")
                .replace("urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08",
                        "urn:iso:std:iso:20022:tech:xsd:pacs.009.001.07");

        assertThatThrownBy(() -> parser.parse(unsupported))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsupported pacs.009 namespace");
    }

    @Test
    void rejectsMultipleFiPaymentTransactions() throws Exception {
        var secondTransaction = """
                <CdtTrfTxInf>
                  <PmtId>
                    <InstrId>FICLIENT01-PACS009-SECOND</InstrId>
                    <EndToEndId>FI-E2E-20260528-SECOND</EndToEndId>
                  </PmtId>
                  <IntrBkSttlmAmt Ccy="USD">1.00</IntrBkSttlmAmt>
                  <IntrBkSttlmDt>2026-05-28</IntrBkSttlmDt>
                </CdtTrfTxInf>
                """;
        var xml = readFixture("pacs009-accepted-nostro.xml")
                .replace("</CdtTrfTxInf>", "</CdtTrfTxInf>" + secondTransaction);

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("one FI payment instruction");
    }

    private String readFixture(String fileName) throws Exception {
        return Files.readString(Path.of("src", "test", "resources", "fi", fileName), StandardCharsets.UTF_8);
    }
}
