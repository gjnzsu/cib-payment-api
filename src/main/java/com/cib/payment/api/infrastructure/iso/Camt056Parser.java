package com.cib.payment.api.infrastructure.iso;

import com.cib.payment.api.application.exception.ValidationFailureException;
import com.cib.payment.api.application.port.Camt056RecallRequestParser;
import java.io.StringReader;
import java.util.Iterator;
import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

@Component
public class Camt056Parser implements Camt056RecallRequestParser {
    private static final String SUPPORTED_NAMESPACE = "urn:iso:std:iso:20022:tech:xsd:camt.056.001.08";
    private static final String SOURCE_MESSAGE_TYPE = "camt.056.001.08";

    @Override
    public ParsedRecallRequest parse(String xml) {
        var document = parseDocument(xml);
        var root = document.getDocumentElement();
        if (root == null || !"Document".equals(root.getLocalName())
                || !SUPPORTED_NAMESPACE.equals(root.getNamespaceURI())) {
            throw new ValidationFailureException("Unsupported camt.056 namespace or message version");
        }
        if (node("/iso:Document/iso:FIToFIPmtCxlReq", document) == null) {
            throw new ValidationFailureException("Unsupported camt.056 message structure");
        }
        if (count("//iso:Undrlyg/iso:TxInf", document) != 1) {
            throw new ValidationFailureException("Only one recall request transaction per request is supported");
        }

        var assignmentId = text("//iso:FIToFIPmtCxlReq/iso:Assgnmt/iso:Id", document);
        return new ParsedRecallRequest(
                assignmentId,
                firstText(document,
                        "//iso:FIToFIPmtCxlReq/iso:Case/iso:Id",
                        "//iso:FIToFIPmtCxlReq/iso:Assgnmt/iso:Id"),
                firstText(document,
                        "//iso:Undrlyg/iso:TxInf/iso:OrgnlEndToEndId",
                        "//iso:Undrlyg/iso:TxInf/iso:OrgnlTxId",
                        "//iso:Undrlyg/iso:TxInf/iso:OrgnlInstrId"),
                text("//iso:Undrlyg/iso:TxInf/iso:CxlRsnInf/iso:Rsn/iso:Cd", document),
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
            throw new ValidationFailureException("Unsupported camt.056 message structure");
        }
    }

    private int count(String expression, Document document) {
        try {
            var xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new SingleNamespaceContext("iso", SUPPORTED_NAMESPACE));
            return ((Number) xpath.evaluate("count(" + expression + ")", document, XPathConstants.NUMBER)).intValue();
        } catch (XPathExpressionException exception) {
            throw new ValidationFailureException("Unsupported camt.056 message structure");
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
