package com.cib.payment.api.infrastructure.iso;

import com.cib.payment.api.domain.model.IsoPaymentCandidate;
import com.cib.payment.api.domain.model.IsoPaymentStatusReport;
import com.cib.payment.api.domain.model.PaymentReason;
import com.cib.payment.api.domain.model.PaymentStatus;
import java.io.StringWriter;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class Pain002Renderer {
    private static final String PAIN_002_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pain.002.001.10";

    public String render(IsoPaymentStatusReport report) {
        try {
            var document = newDocument();
            var root = element(document, "Document");
            root.setAttribute("xmlns", PAIN_002_NAMESPACE);
            document.appendChild(root);

            var customerPaymentStatusReport = append(document, root, "CstmrPmtStsRpt");
            appendGroupHeader(document, customerPaymentStatusReport, report);
            appendOriginalGroupInformation(document, customerPaymentStatusReport, report.originalCandidate());
            appendOriginalPaymentInformation(document, customerPaymentStatusReport, report);

            return serialize(document);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to render pain.002 status report", ex);
        }
    }

    private Document newDocument() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().newDocument();
    }

    private void appendGroupHeader(Document document, Element parent, IsoPaymentStatusReport report) {
        var groupHeader = append(document, parent, "GrpHdr");
        text(document, groupHeader, "MsgId", "pain002-" + report.paymentId().value());
        text(document, groupHeader, "CreDtTm", report.reportCreatedAt().toString());
        var initiatingParty = append(document, groupHeader, "InitgPty");
        text(document, initiatingParty, "Nm", report.originalCandidate().debtor().accountName());
    }

    private void appendOriginalGroupInformation(Document document, Element parent, IsoPaymentCandidate candidate) {
        var originalGroupInformation = append(document, parent, "OrgnlGrpInfAndSts");
        textIfPresent(document, originalGroupInformation, "OrgnlMsgId", candidate.messageId());
        text(document, originalGroupInformation, "OrgnlMsgNmId", candidate.sourceMessageType());
    }

    private void appendOriginalPaymentInformation(
            Document document,
            Element parent,
            IsoPaymentStatusReport report) {
        var candidate = report.originalCandidate();
        var originalPaymentInformation = append(document, parent, "OrgnlPmtInfAndSts");
        textIfPresent(document, originalPaymentInformation, "OrgnlPmtInfId", candidate.paymentInformationId());

        var transactionInformation = append(document, originalPaymentInformation, "TxInfAndSts");
        textIfPresent(document, transactionInformation, "OrgnlInstrId", candidate.instructionId());
        textIfPresent(document, transactionInformation, "OrgnlEndToEndId", candidate.endToEndId());
        text(document, transactionInformation, "AcctSvcrRef", report.paymentId().value().toString());
        text(document, transactionInformation, "TxSts", isoTransactionStatus(report.internalStatus()));
        appendReasonIfNeeded(document, transactionInformation, report);
        appendOriginalTransactionReference(document, transactionInformation, candidate);
    }

    private void appendReasonIfNeeded(
            Document document,
            Element transactionInformation,
            IsoPaymentStatusReport report) {
        if (report.internalStatus() == PaymentStatus.COMPLETED) {
            return;
        }

        var reasonInformation = append(document, transactionInformation, "StsRsnInf");
        var reason = append(document, reasonInformation, "Rsn");
        text(document, reason, "Cd", isoReasonCode(report.internalStatus(), report.reason()));
        text(document, reasonInformation, "AddtlInf", additionalInformation(report.internalStatus(), report.reason()));
    }

    private void appendOriginalTransactionReference(
            Document document,
            Element transactionInformation,
            IsoPaymentCandidate candidate) {
        var originalTransactionReference = append(document, transactionInformation, "OrgnlTxRef");
        var amount = append(document, originalTransactionReference, "Amt");
        var instructedAmount = element(document, "InstdAmt");
        instructedAmount.setAttribute("Ccy", candidate.amount().currency());
        instructedAmount.setTextContent(candidate.amount().value());
        amount.appendChild(instructedAmount);

        var debtor = append(document, originalTransactionReference, "Dbtr");
        var debtorParty = append(document, debtor, "Pty");
        text(document, debtorParty, "Nm", candidate.debtor().accountName());

        var creditor = append(document, originalTransactionReference, "Cdtr");
        var creditorParty = append(document, creditor, "Pty");
        text(document, creditorParty, "Nm", candidate.beneficiary().accountName());
    }

    private String isoTransactionStatus(PaymentStatus status) {
        return switch (status) {
            case COMPLETED -> "ACSC";
            case REJECTED, FAILED -> "RJCT";
            case PROCESSING, TIMEOUT -> "PDNG";
            case ACCEPTED -> "PDNG";
        };
    }

    private String isoReasonCode(PaymentStatus status, Optional<PaymentReason> reason) {
        if (status == PaymentStatus.PROCESSING || status == PaymentStatus.ACCEPTED) {
            return "SL01";
        }
        if (status == PaymentStatus.TIMEOUT) {
            return "NARR";
        }
        if (status == PaymentStatus.FAILED) {
            return "MS03";
        }

        return reason
                .map(PaymentReason::code)
                .map(this::rejectedReasonCode)
                .orElse("AC01");
    }

    private String rejectedReasonCode(String internalReasonCode) {
        return switch (internalReasonCode) {
            case "HK_SUSPICIOUS_PROXY_OR_ACCOUNT" -> "FRAD";
            case "HK_UNSUPPORTED_CURRENCY" -> "CURR";
            case "HK_UNKNOWN_PARTICIPANT" -> "AGNT";
            default -> "AC01";
        };
    }

    private String additionalInformation(PaymentStatus status, Optional<PaymentReason> reason) {
        var detail = reason
                .map(paymentReason -> paymentReason.code() + ": " + paymentReason.message())
                .orElseGet(() -> defaultReasonDetail(status));
        return switch (status) {
            case PROCESSING, ACCEPTED -> detail + " (normal processing)";
            case TIMEOUT -> detail + " (operational intervention may be required)";
            case FAILED -> detail + " (simulator internal failure)";
            default -> detail;
        };
    }

    private String defaultReasonDetail(PaymentStatus status) {
        return switch (status) {
            case PROCESSING, ACCEPTED -> "HK_PENDING_PROCESSING: Payment remains pending";
            case TIMEOUT -> "HK_SIMULATOR_TIMEOUT: Payment remains pending after simulator timeout";
            case FAILED -> "HK_SIMULATOR_INTERNAL_FAILURE: HK simulator failed internally";
            case REJECTED -> "HK_CLEARING_REJECTION: Payment rejected by HK clearing simulator";
            case COMPLETED -> "";
        };
    }

    private Element append(Document document, Element parent, String name) {
        var child = element(document, name);
        parent.appendChild(child);
        return child;
    }

    private Element element(Document document, String name) {
        return document.createElementNS(PAIN_002_NAMESPACE, name);
    }

    private void text(Document document, Element parent, String name, String value) {
        var child = append(document, parent, name);
        child.setTextContent(value == null ? "" : value);
    }

    private void textIfPresent(Document document, Element parent, String name, String value) {
        if (value != null && !value.isBlank()) {
            text(document, parent, name, value);
        }
    }

    private String serialize(Document document) throws Exception {
        var transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.STANDALONE, "no");
        var writer = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(writer));
        return writer.toString();
    }
}
