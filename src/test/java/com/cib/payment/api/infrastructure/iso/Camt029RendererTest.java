package com.cib.payment.api.infrastructure.iso;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.domain.model.AccountRelationshipRole;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.CorrespondentSettlementContext;
import com.cib.payment.api.domain.model.FiParty;
import com.cib.payment.api.domain.model.FiPaymentId;
import com.cib.payment.api.domain.model.RecallInvestigationId;
import com.cib.payment.api.domain.model.RecallInvestigationRecord;
import com.cib.payment.api.domain.model.RecallInvestigationStatus;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class Camt029RendererTest {
    private static final String CAMT_029_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:camt.029.001.09";
    private static final FiPaymentId PAYMENT_ID =
            new FiPaymentId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private static final RecallInvestigationId INVESTIGATION_ID =
            new RecallInvestigationId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final Instant CREATED_AT = Instant.parse("2026-05-28T10:00:00Z");

    private final Camt029Renderer renderer = new Camt029Renderer();

    @Test
    void rendersAcceptedResolutionWithOriginalRecallIdentifiersAndCorrelationTraceability() throws Exception {
        var xml = renderer.render(record(
                RecallInvestigationStatus.ACCEPTED,
                Optional.of("AC01"),
                Optional.of("FICLIENT01 CANCEL ACCEPTED")));

        var document = parse(xml);
        assertThat(document.getDocumentElement().getNamespaceURI()).isEqualTo(CAMT_029_NAMESPACE);
        assertThat(textOf(document, "MsgId")).isEqualTo("camt029-22222222-2222-2222-2222-222222222222");
        assertThat(textOf(document, "Id")).isEqualTo("camt029-22222222-2222-2222-2222-222222222222");
        assertThat(textOf(document, "OrgnlMsgId")).isEqualTo("FICLIENT01-CAMT056-RECALL-ACCEPTED");
        assertThat(textOf(document, "OrgnlMsgNmId")).isEqualTo("camt.056.001.08");
        assertThat(textOf(document, "CaseId")).isEqualTo("FICLIENT01-CASE-001");
        assertThat(textOf(document, "OrgnlEndToEndId")).isEqualTo("FI-E2E-20260528-0001");
        assertThat(textOf(document, "Conf")).isEqualTo("CNCL");
        assertThat(textOf(document, "Cd")).isEqualTo("AC01");
        assertThat(textOf(document, "AddtlInf")).contains("FICLIENT01 CANCEL ACCEPTED");
        assertThat(textOf(document, "CorrelationId")).isEqualTo("corr-fi-recall");
    }

    @Test
    void rendersRejectedResolutionWithReasonDetails() throws Exception {
        var document = parse(renderer.render(record(
                RecallInvestigationStatus.REJECTED,
                Optional.of("NOAS"),
                Optional.of("Correspondent reports no assignable cancellation"))));

        assertThat(textOf(document, "Conf")).isEqualTo("RJCR");
        assertThat(textOf(document, "Cd")).isEqualTo("NOAS");
        assertThat(textOf(document, "AddtlInf"))
                .contains("NOAS")
                .contains("Correspondent reports no assignable cancellation");
        assertThat(textOf(document, "OrgnlEndToEndId")).isEqualTo("FI-E2E-20260528-0001");
        assertThat(textOf(document, "CorrelationId")).isEqualTo("corr-fi-recall");
    }

    @Test
    void rendersPendingResolutionWithReasonDetails() throws Exception {
        var document = parse(renderer.render(record(
                RecallInvestigationStatus.PENDING,
                Optional.of("IPAY"),
                Optional.of("Investigation pending correspondent response"))));

        assertThat(textOf(document, "Conf")).isEqualTo("PDCR");
        assertThat(textOf(document, "Cd")).isEqualTo("IPAY");
        assertThat(textOf(document, "AddtlInf"))
                .contains("IPAY")
                .contains("Investigation pending correspondent response");
        assertThat(textOf(document, "OrgnlMsgId")).isEqualTo("FICLIENT01-CAMT056-RECALL-ACCEPTED");
        assertThat(textOf(document, "CaseId")).isEqualTo("FICLIENT01-CASE-001");
        assertThat(textOf(document, "OrgnlEndToEndId")).isEqualTo("FI-E2E-20260528-0001");
        assertThat(textOf(document, "CorrelationId")).isEqualTo("corr-fi-recall");
    }

    private RecallInvestigationRecord record(
            RecallInvestigationStatus status,
            Optional<String> reasonCode,
            Optional<String> reasonMessage) {
        return new RecallInvestigationRecord(
                INVESTIGATION_ID,
                PAYMENT_ID,
                "fi-client-a",
                "FICLIENT01-CAMT056-RECALL-ACCEPTED",
                "FICLIENT01-CASE-001",
                "FI-E2E-20260528-0001",
                status,
                reasonCode,
                reasonMessage,
                settlementContext(),
                new CorrelationId("corr-fi-recall"),
                CREATED_AT,
                CREATED_AT);
    }

    private CorrespondentSettlementContext settlementContext() {
        return new CorrespondentSettlementContext(
                new FiParty("CIBBHKHH"),
                new FiParty("CORRUS33"),
                Optional.of(new FiParty("CORRUS33")),
                "USD",
                AccountRelationshipRole.NOSTRO,
                "nostro-usd-corrus33-****1234");
    }

    private Document parse(String xml) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String textOf(Document document, String elementName) {
        return document.getElementsByTagNameNS("*", elementName).item(0).getTextContent();
    }
}
