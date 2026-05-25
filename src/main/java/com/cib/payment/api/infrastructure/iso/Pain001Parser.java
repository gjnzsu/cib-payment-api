package com.cib.payment.api.infrastructure.iso;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.Pain001PaymentInitiationParser;
import com.cib.payment.api.domain.model.AccountReference;
import com.cib.payment.api.domain.model.Money;
import java.io.StringReader;
import java.util.Iterator;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.springframework.stereotype.Component;

@Component
public class Pain001Parser implements Pain001PaymentInitiationParser {
    private static final String SUPPORTED_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09";
    private static final String SOURCE_MESSAGE_TYPE = "pain.001.001.09";

    @Override
    public ParsedInitiation parse(String xml) {
        var document = parseDocument(xml);
        var root = document.getDocumentElement();
        if (root == null || !"Document".equals(root.getLocalName())
                || !SUPPORTED_NAMESPACE.equals(root.getNamespaceURI())) {
            throw new ValidationFailureException("Unsupported pain.001 namespace or message version");
        }
        if (node("/iso:Document/iso:CstmrCdtTrfInitn", document) == null) {
            throw new ValidationFailureException("Unsupported pain.001 message structure");
        }
        if (count("//iso:PmtInf", document) != 1 || count("//iso:CdtTrfTxInf", document) != 1) {
            throw new ValidationFailureException("Only one payment instruction per request is supported");
        }

        var instructedAmount = (Element) node("//iso:CdtTrfTxInf/iso:Amt/iso:InstdAmt", document);
        var currency = instructedAmount == null ? null : blankToNull(instructedAmount.getAttribute("Ccy"));
        var amountValue = instructedAmount == null ? null : blankToNull(instructedAmount.getTextContent());

        return new ParsedInitiation(
                text("//iso:GrpHdr/iso:MsgId", document),
                text("//iso:PmtInf/iso:PmtInfId", document),
                new AccountReference(
                        text("//iso:DbtrAgt/iso:FinInstnId/iso:BICFI", document),
                        text("//iso:DbtrAcct/iso:Id/iso:Othr/iso:Id", document),
                        text("//iso:Dbtr/iso:Nm", document)),
                text("//iso:CdtTrfTxInf/iso:Cdtr/iso:Nm", document),
                text("//iso:CdtTrfTxInf/iso:CdtrAcct/iso:Id/iso:Othr/iso:Id", document),
                text("//iso:CdtTrfTxInf/iso:CdtrAcct/iso:Prxy/iso:Id", document),
                firstText(document,
                        "//iso:CdtTrfTxInf/iso:CdtrAcct/iso:Prxy/iso:Tp/iso:Cd",
                        "//iso:CdtTrfTxInf/iso:CdtrAcct/iso:Prxy/iso:Tp/iso:Prtry"),
                text("//iso:CdtTrfTxInf/iso:CdtrAgt/iso:FinInstnId/iso:BICFI", document),
                new Money(currency, amountValue),
                text("//iso:CdtTrfTxInf/iso:PmtId/iso:EndToEndId", document),
                text("//iso:CdtTrfTxInf/iso:PmtId/iso:InstrId", document),
                text("//iso:CdtTrfTxInf/iso:RmtInf/iso:Ustrd", document),
                text("//iso:CdtTrfTxInf/iso:RmtInf/iso:Strd/iso:CdtrRefInf/iso:Ref", document),
                text("//iso:CdtTrfTxInf/iso:Purp/iso:Cd", document),
                text("//iso:PmtInf/iso:PmtTpInf/iso:CtgyPurp/iso:Cd", document),
                SOURCE_MESSAGE_TYPE);
    }

    private Document parseDocument(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            var builder = factory.newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
            builder.setErrorHandler(new QuietXmlErrorHandler());
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (SAXException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("DOCTYPE")) {
                throw new ValidationFailureException("Unsafe XML is not supported");
            }
            throw new ValidationFailureException("Malformed XML");
        } catch (Exception exception) {
            throw new ValidationFailureException("Malformed XML");
        }
    }

    private Node node(String expression, Document document) {
        try {
            var xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new SingleNamespaceContext("iso", SUPPORTED_NAMESPACE));
            return (Node) xpath.evaluate(expression, document, XPathConstants.NODE);
        } catch (XPathExpressionException exception) {
            throw new ValidationFailureException("Unsupported pain.001 message structure");
        }
    }

    private int count(String expression, Document document) {
        try {
            var xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new SingleNamespaceContext("iso", SUPPORTED_NAMESPACE));
            return ((Number) xpath.evaluate("count(" + expression + ")", document, XPathConstants.NUMBER)).intValue();
        } catch (XPathExpressionException exception) {
            throw new ValidationFailureException("Unsupported pain.001 message structure");
        }
    }

    private String text(String expression, Document document) {
        var node = node(expression, document);
        return node == null ? null : blankToNull(node.getTextContent());
    }

    private String firstText(Document document, String... expressions) {
        for (var expression : expressions) {
            var value = text(expression, document);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static class SingleNamespaceContext implements NamespaceContext {
        private final String prefix;
        private final String namespaceUri;

        SingleNamespaceContext(String prefix, String namespaceUri) {
            this.prefix = prefix;
            this.namespaceUri = namespaceUri;
        }

        @Override
        public String getNamespaceURI(String requestedPrefix) {
            if (prefix.equals(requestedPrefix)) {
                return namespaceUri;
            }
            if (XMLConstants.XML_NS_PREFIX.equals(requestedPrefix)) {
                return XMLConstants.XML_NS_URI;
            }
            if (XMLConstants.XMLNS_ATTRIBUTE.equals(requestedPrefix)) {
                return XMLConstants.XMLNS_ATTRIBUTE_NS_URI;
            }
            return XMLConstants.NULL_NS_URI;
        }

        @Override
        public String getPrefix(String requestedNamespaceUri) {
            return namespaceUri.equals(requestedNamespaceUri) ? prefix : null;
        }

        @Override
        public Iterator<String> getPrefixes(String requestedNamespaceUri) {
            var matchingPrefix = getPrefix(requestedNamespaceUri);
            return matchingPrefix == null
                    ? java.util.Collections.emptyIterator()
                    : java.util.List.of(matchingPrefix).iterator();
        }
    }

    private static class QuietXmlErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void error(SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(SAXParseException exception) throws SAXException {
            throw exception;
        }
    }
}
