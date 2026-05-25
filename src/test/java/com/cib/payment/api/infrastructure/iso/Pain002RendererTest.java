package com.cib.payment.api.infrastructure.iso;

import static org.assertj.core.api.Assertions.assertThat;

import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.BeneficiaryIdentifier;
import com.cib.payment.api.domain.model.CorrelationId;
import com.cib.payment.api.domain.model.IsoPaymentCandidate;
import com.cib.payment.api.domain.model.IsoPaymentStatusReport;
import com.cib.payment.api.domain.model.Money;
import com.cib.payment.api.domain.model.PaymentId;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentStatus;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class Pain002RendererTest {
    private static final Instant REPORT_CREATED_AT = Instant.parse("2026-05-24T10:15:30Z");
    private static final PaymentId PAYMENT_ID =
            new PaymentId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

    private final Pain002Renderer renderer = new Pain002Renderer();

    @Test
    void rendersCompletedReportWithOriginalIdentifiersAndEnginePaymentLinkage() throws Exception {
        var xml = renderer.render(report(PaymentStatus.COMPLETED, Optional.empty()));

        var document = parse(xml);
        assertThat(textOf(document, "MsgId")).isEqualTo("pain002-11111111-1111-1111-1111-111111111111");
        assertThat(textOf(document, "CreDtTm")).isEqualTo("2026-05-24T10:15:30Z");
        assertThat(textOf(document, "OrgnlMsgId")).isEqualTo("MSG-20260524-0001");
        assertThat(textOf(document, "OrgnlMsgNmId")).isEqualTo("pain.001.001.09");
        assertThat(textOf(document, "OrgnlPmtInfId")).isEqualTo("PMT-20260524-SUCCESS");
        assertThat(textOf(document, "OrgnlInstrId")).isEqualTo("INSTR-20260524-0001");
        assertThat(textOf(document, "OrgnlEndToEndId")).isEqualTo("INV-2026-0001");
        assertThat(textOf(document, "AcctSvcrRef")).isEqualTo(PAYMENT_ID.value().toString());
        assertThat(textOf(document, "TxSts")).isEqualTo("ACSC");
        assertThat(elements(document, "StsRsnInf")).isZero();
    }

    @Test
    void rendersRejectedReportWithIsoReasonDetailsAndEscapedUserText() throws Exception {
        var xml = renderer.render(report(
                PaymentStatus.REJECTED,
                Optional.of(new PaymentReason(
                        "HK_SUSPICIOUS_PROXY_OR_ACCOUNT",
                        "Beneficiary proxy or account flagged by HK simulator"))));

        assertThat(xml).contains("Acme &amp; Co &lt;HK&gt;");
        assertThat(xml).doesNotContain("Acme & Co <HK>");

        var document = parse(xml);
        assertThat(textOf(document, "TxSts")).isEqualTo("RJCT");
        assertThat(textOf(document, "Cd")).isEqualTo("FRAD");
        assertThat(textOf(document, "AddtlInf"))
                .contains("HK_SUSPICIOUS_PROXY_OR_ACCOUNT")
                .contains("Beneficiary proxy or account flagged by HK simulator");
        assertThat(textOf(document, "Nm")).isEqualTo("Acme & Co <HK>");
    }

    @Test
    void rendersDistinctPendingReasonsForNormalProcessingAndTimeout() throws Exception {
        var processingXml = renderer.render(report(
                PaymentStatus.PROCESSING,
                Optional.of(new PaymentReason("HK_PENDING_PROCESSING", "Payment remains pending in HK simulator"))));
        var timeoutXml = renderer.render(report(
                PaymentStatus.TIMEOUT,
                Optional.of(new PaymentReason("HK_SIMULATOR_TIMEOUT", "HK simulator timed out before settlement"))));

        var processing = parse(processingXml);
        var timeout = parse(timeoutXml);

        assertThat(textOf(processing, "TxSts")).isEqualTo("PDNG");
        assertThat(textOf(processing, "Cd")).isEqualTo("SL01");
        assertThat(textOf(processing, "AddtlInf")).contains("normal processing");

        assertThat(textOf(timeout, "TxSts")).isEqualTo("PDNG");
        assertThat(textOf(timeout, "Cd")).isEqualTo("NARR");
        assertThat(textOf(timeout, "AddtlInf")).contains("operational intervention");
    }

    @Test
    void rendersFailedInternalSimulatorOutcomeAsRejectedStatusReport() throws Exception {
        var xml = renderer.render(report(
                PaymentStatus.FAILED,
                Optional.of(new PaymentReason("HK_SIMULATOR_INTERNAL_FAILURE", "HK simulator failed internally"))));

        var document = parse(xml);
        assertThat(textOf(document, "TxSts")).isEqualTo("RJCT");
        assertThat(textOf(document, "Cd")).isEqualTo("MS03");
        assertThat(textOf(document, "AddtlInf"))
                .contains("HK_SIMULATOR_INTERNAL_FAILURE")
                .contains("HK simulator failed internally");
    }

    private IsoPaymentStatusReport report(PaymentStatus status, Optional<PaymentReason> reason) {
        return new IsoPaymentStatusReport(
                PAYMENT_ID,
                candidate(),
                status,
                REPORT_CREATED_AT,
                new CorrelationId("corr-pain002"),
                reason);
    }

    private IsoPaymentCandidate candidate() {
        return new IsoPaymentCandidate(
                new AccountReference("CIBBHKHH", "000123456789", "Acme & Co <HK>"),
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

    private int elements(Document document, String elementName) {
        return document.getElementsByTagNameNS("*", elementName).getLength();
    }
}
