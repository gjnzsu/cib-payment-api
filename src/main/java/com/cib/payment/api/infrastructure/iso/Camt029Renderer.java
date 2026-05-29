package com.cib.payment.api.infrastructure.iso;

import com.cib.payment.api.application.port.RecallInvestigationResponseRenderer;
import com.cib.payment.api.domain.model.RecallInvestigationRecord;
import com.cib.payment.api.domain.model.RecallInvestigationStatus;
import java.io.StringWriter;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

@Component
public class Camt029Renderer implements RecallInvestigationResponseRenderer {
    private static final String CAMT_029_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:camt.029.001.09";
    private static final String ORIGINAL_MESSAGE_TYPE = "camt.056.001.08";

    public String render(RecallInvestigationRecord record) {
        try {
            var document = newDocument();
            var root = element(document, "Document");
            root.setAttribute("xmlns", CAMT_029_NAMESPACE);
            document.appendChild(root);

            var resolution = append(document, root, "RsltnOfInvstgtn");
            appendGroupHeader(document, resolution, record);
            appendAssignment(document, resolution, record);
            appendStatus(document, resolution, record.status());
            appendCancellationDetails(document, resolution, record);
            appendSupplementaryData(document, resolution, record);

            return serialize(document);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to render camt.029 resolution", ex);
        }
    }

    private Document newDocument() throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        return factory.newDocumentBuilder().newDocument();
    }

    private void appendGroupHeader(Document document, Element parent, RecallInvestigationRecord record) {
        var groupHeader = append(document, parent, "GrpHdr");
        text(document, groupHeader, "MsgId", messageId(record));
        text(document, groupHeader, "CreDtTm", record.updatedAt().toString());
    }

    private void appendAssignment(Document document, Element parent, RecallInvestigationRecord record) {
        var assignment = append(document, parent, "Assgnmt");
        text(document, assignment, "Id", messageId(record));

        var assigner = append(document, assignment, "Assgnr");
        var assignerAgent = append(document, assigner, "Agt");
        var assignerFinancialInstitution = append(document, assignerAgent, "FinInstnId");
        text(document, assignerFinancialInstitution, "BICFI", record.settlementContext().instructedAgent().bic());

        var assignee = append(document, assignment, "Assgne");
        var assigneeAgent = append(document, assignee, "Agt");
        var assigneeFinancialInstitution = append(document, assigneeAgent, "FinInstnId");
        text(document, assigneeFinancialInstitution, "BICFI", record.settlementContext().instructingAgent().bic());

        text(document, assignment, "CreDtTm", record.updatedAt().toString());
    }

    private void appendStatus(Document document, Element parent, RecallInvestigationStatus status) {
        var statusElement = append(document, parent, "Sts");
        text(document, statusElement, "Conf", confirmationCode(status));
    }

    private void appendCancellationDetails(Document document, Element parent, RecallInvestigationRecord record) {
        var cancellationDetails = append(document, parent, "CxlDtls");
        text(document, cancellationDetails, "OrgnlMsgId", record.recallMessageId());
        text(document, cancellationDetails, "OrgnlMsgNmId", ORIGINAL_MESSAGE_TYPE);
        text(document, cancellationDetails, "CaseId", record.caseId());

        var transactionInformation = append(document, cancellationDetails, "TxInfAndSts");
        text(document, transactionInformation, "OrgnlEndToEndId", record.originalPaymentReference());
        text(document, transactionInformation, "CxlStsId", record.investigationId().value().toString());
        appendReason(document, transactionInformation, record);
    }

    private void appendReason(Document document, Element parent, RecallInvestigationRecord record) {
        var reasonInformation = append(document, parent, "CxlStsRsnInf");
        var reason = append(document, reasonInformation, "Rsn");
        text(document, reason, "Cd", reasonCode(record));
        text(document, reasonInformation, "AddtlInf", reasonDetail(record));
    }

    private void appendSupplementaryData(Document document, Element parent, RecallInvestigationRecord record) {
        var supplementaryData = append(document, parent, "SplmtryData");
        var envelope = append(document, supplementaryData, "Envlp");
        text(document, envelope, "CorrelationId", record.correlationId().value());
        text(document, envelope, "FiPaymentId", record.fiPaymentId().value().toString());
    }

    private String messageId(RecallInvestigationRecord record) {
        return "camt029-" + record.investigationId().value();
    }

    private String confirmationCode(RecallInvestigationStatus status) {
        return switch (status) {
            case ACCEPTED -> "CNCL";
            case REJECTED -> "RJCR";
            case PENDING -> "PDCR";
        };
    }

    private String reasonCode(RecallInvestigationRecord record) {
        return record.reasonCode().orElseGet(() -> switch (record.status()) {
            case ACCEPTED -> "AC01";
            case REJECTED -> "NOAS";
            case PENDING -> "IPAY";
        });
    }

    private String reasonDetail(RecallInvestigationRecord record) {
        var code = reasonCode(record);
        return record.reasonMessage()
                .map(message -> code + ": " + message)
                .orElseGet(() -> switch (record.status()) {
                    case ACCEPTED -> code + ": Recall accepted by correspondent simulator";
                    case REJECTED -> code + ": Recall rejected by correspondent simulator";
                    case PENDING -> code + ": Investigation pending correspondent response";
                });
    }

    private Element append(Document document, Element parent, String name) {
        var child = element(document, name);
        parent.appendChild(child);
        return child;
    }

    private Element element(Document document, String name) {
        return document.createElementNS(CAMT_029_NAMESPACE, name);
    }

    private void text(Document document, Element parent, String name, String value) {
        var child = append(document, parent, name);
        child.setTextContent(value == null ? "" : value);
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
