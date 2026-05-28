package com.cib.payment.api.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.domain.model.AccountRelationshipRole;
import com.cib.payment.api.domain.model.AuthorizationContext;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.CorrespondentSettlementContext;
import com.cib.payment.api.domain.model.FiParty;
import com.cib.payment.api.domain.model.FiPaymentId;
import com.cib.payment.api.domain.model.FiPaymentIdentifiers;
import com.cib.payment.api.domain.model.FiPaymentRecord;
import com.cib.payment.api.domain.model.FiPaymentStatus;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.RecallInvestigationId;
import com.cib.payment.api.domain.model.RecallInvestigationRecord;
import com.cib.payment.api.domain.model.RecallInvestigationStatus;
import com.cib.payment.api.infrastructure.observability.AccountNumberMasker;
import com.cib.payment.api.infrastructure.observability.MicrometerPaymentObservability;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class FiSensitiveLoggingIntegrationTest {
    @Test
    void maskerOmitsRawFiXmlPayloads() {
        var pacs009 = """
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08">
                  <Acct><Id><Othr><Id>CORRUS33-USD-1234567890121234</Id></Othr></Id></Acct>
                </Document>
                """;
        var camt056 = """
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.056.001.08">
                  <Case><Id>CASE-FI-20260528-0001</Id></Case>
                </Document>
                """;
        var camt029 = """
                <Document xmlns="urn:iso:std:iso:20022:tech:xsd:camt.029.001.09">
                  <Sts>ACCP</Sts>
                </Document>
                """;

        assertThat(AccountNumberMasker.maskSensitive(pacs009)).isEqualTo("[FI_XML_PAYLOAD_OMITTED]");
        assertThat(AccountNumberMasker.maskSensitive(camt056)).isEqualTo("[FI_XML_PAYLOAD_OMITTED]");
        assertThat(AccountNumberMasker.maskSensitive(camt029)).isEqualTo("[FI_XML_PAYLOAD_OMITTED]");
    }

    @Test
    void maskerRedactsBicLinkedAndSimulatedCorrespondentAccountReferences() {
        assertThat(AccountNumberMasker.maskSensitive("CORRUS33-USD-1234567890121234"))
                .isEqualTo("CORRUS33-USD-************1234");
        assertThat(AccountNumberMasker.maskSensitive("nostro-usd-corrus33-1234567890121234"))
                .isEqualTo("nostro-usd-corrus33-************1234");
        assertThat(AccountNumberMasker.maskSensitive("vostro-usd-vostus33-9876543210985678"))
                .isEqualTo("vostro-usd-vostus33-************5678");
        assertThat(AccountNumberMasker.maskSensitive("loro-usd-lorous33-0000111122229012"))
                .isEqualTo("loro-usd-lorous33-************9012");
    }

    @Test
    void fiObservabilityLogsMaskedContextWithoutRawXmlFullAccountsOrBearerTokens(CapturedOutput output) {
        var observability = new MicrometerPaymentObservability(new SimpleMeterRegistry());
        var record = fiPaymentRecord("nostro-usd-corrus33-1234567890121234");
        var recall = recallRecord(record, "CORRUS33-USD-1234567890121234");
        var rawXml = "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.009.001.08\">"
                + "<Id>CORRUS33-USD-1234567890121234</Id></Document>";

        observability.fiPaymentAccepted(record, authorizationContext());
        observability.fiPaymentStatusLookup(record, authorizationContext());
        observability.recallInvestigationCreated(recall, authorizationContext());
        observability.fiXmlPayloadHandled("pacs.009", rawXml, record.correlationId());

        assertThat(output).contains("fi_payment_accepted");
        assertThat(output).contains("fi_payment_status_lookup");
        assertThat(output).contains("fi_recall_investigation_created");
        assertThat(output).contains("fi_xml_payload_handled");
        assertThat(output).contains("correlationId=corr-fi-sensitive");
        assertThat(output).contains("simulatedAccount=nostro-usd-corrus33-************1234");
        assertThat(output).contains("originalReference=CORRUS33-USD-************1234");
        assertThat(output).contains("xmlPayload=[FI_XML_PAYLOAD_OMITTED]");

        assertThat(output).doesNotContain("<Document");
        assertThat(output).doesNotContain("Bearer ");
        assertThat(output).doesNotContain("1234567890121234");
        assertThat(output).doesNotContain("9876543210985678");
    }

    private FiPaymentRecord fiPaymentRecord(String simulatedAccountReference) {
        var now = Instant.parse("2026-05-28T10:15:30Z");
        return new FiPaymentRecord(
                new FiPaymentId(UUID.fromString("11111111-1111-1111-1111-111111111111")),
                "fi-client-a",
                new FiPaymentIdentifiers(
                        "FI-MSG-20260528-ACCEPTED-NOSTRO",
                        "FICLIENT01-PACS009-ACCEPTED-NOSTRO",
                        "CORRUS33-USD-1234567890121234"),
                new FiParty("CIBBHKHH"),
                new FiParty("CORRUS33"),
                new Money("USD", "1250.00"),
                "USD",
                FiPaymentStatus.SETTLED,
                new CorrespondentSettlementContext(
                        new FiParty("CIBBHKHH"),
                        new FiParty("CORRUS33"),
                        Optional.of(new FiParty("CORRUS33")),
                        "USD",
                        AccountRelationshipRole.NOSTRO,
                        simulatedAccountReference),
                new CorrelationId("corr-fi-sensitive"),
                now,
                now,
                Optional.empty());
    }

    private RecallInvestigationRecord recallRecord(FiPaymentRecord payment, String originalReference) {
        var now = Instant.parse("2026-05-28T10:20:30Z");
        return new RecallInvestigationRecord(
                new RecallInvestigationId(UUID.fromString("22222222-2222-2222-2222-222222222222")),
                payment.paymentId(),
                payment.ownerClientId(),
                "FI-RECALL-20260528-0001",
                "CASE-FI-20260528-0001",
                originalReference,
                RecallInvestigationStatus.ACCEPTED,
                Optional.empty(),
                Optional.empty(),
                payment.correspondentSettlementContext(),
                payment.correlationId(),
                now,
                now);
    }

    private AuthorizationContext authorizationContext() {
        return new AuthorizationContext(
                "fi-client-a",
                "fi-client-a",
                Set.of("fi-payments:create", "fi-payments:read", "fi-payments:investigate"),
                null,
                Map.of(),
                Instant.parse("2026-05-28T10:00:00Z"),
                "jwt-fi-sensitive",
                new CorrelationId("corr-fi-sensitive"));
    }
}
