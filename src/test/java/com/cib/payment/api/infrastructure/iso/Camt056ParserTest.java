package com.cib.payment.api.infrastructure.iso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cib.payment.api.application.exception.ValidationFailureException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Camt056ParserTest {
    private final Camt056Parser parser = new Camt056Parser();

    @Test
    void parsesSupportedCamt056RecallFieldsFromFixture() throws Exception {
        var parsed = parser.parse(readFixture("camt056-recall-accepted.xml"));

        assertThat(parsed.messageId()).isEqualTo("FICLIENT01-CAMT056-RECALL-ACCEPTED");
        assertThat(parsed.caseId()).isEqualTo("FICLIENT01-CAMT056-RECALL-ACCEPTED");
        assertThat(parsed.originalPaymentReference()).isEqualTo("FI-E2E-20260528-0001");
        assertThat(parsed.reasonCode()).isEqualTo("DUPL");
        assertThat(parsed.sourceMessageType()).isEqualTo("camt.056.001.08");
    }

    @Test
    void rejectsUnsupportedNamespaceOrMessageVersion() throws Exception {
        var unsupported = readFixture("camt056-recall-accepted.xml")
                .replace("urn:iso:std:iso:20022:tech:xsd:camt.056.001.08",
                        "urn:iso:std:iso:20022:tech:xsd:camt.056.001.07");

        assertThatThrownBy(() -> parser.parse(unsupported))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsupported camt.056 namespace");
    }

    @Test
    void rejectsMalformedXml() throws Exception {
        assertThatThrownBy(() -> parser.parse(readFixture("camt056-malformed.xml")))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Malformed XML");
    }

    @Test
    void rejectsDoctypeAndExternalEntityXml() throws Exception {
        assertThatThrownBy(() -> parser.parse(readFixture("camt056-unsafe.xml")))
                .isInstanceOf(ValidationFailureException.class)
                .hasMessageContaining("Unsafe XML");
    }

    private String readFixture(String fileName) throws Exception {
        return Files.readString(Path.of("src", "test", "resources", "fi", fileName), StandardCharsets.UTF_8);
    }
}
