package com.example.dynamicxmlrules.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.example.dynamicxmlrules.engine.WorkflowExecutionException;

/**
 * Parser DOM endurecido contra XXE (XML External Entity).
 * <ul>
 *   <li>{@code DOCTYPE} proibido;</li>
 *   <li>entidades externas gerais e de parâmetro desabilitadas;</li>
 *   <li>carregamento de DTDs externas desabilitado;</li>
 *   <li>{@link XMLConstants#ACCESS_EXTERNAL_DTD} e {@link XMLConstants#ACCESS_EXTERNAL_SCHEMA} vazios;</li>
 *   <li>XInclude e expansão de entidades desabilitados.</li>
 * </ul>
 */
@Component
public class SecureXmlParser {

    private static final ErrorHandler STRICT_ERROR_HANDLER = new ErrorHandler() {
        @Override
        public void warning(SAXParseException e) {
            // avisos são ignorados
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    };

    private final DocumentBuilderFactory factory = createSecureFactory();

    public Document parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new WorkflowExecutionException("Conteúdo XML vazio.");
        }
        return parse(new InputSource(new StringReader(xml)));
    }

    public Document parse(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(new InputSource(in));
        } catch (IOException e) {
            throw new WorkflowExecutionException("Não foi possível ler o arquivo XML: " + file.getFileName(), e);
        }
    }

    private Document parse(InputSource source) {
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(STRICT_ERROR_HANDLER);
            // Defesa em profundidade: qualquer tentativa de resolver entidade externa é rejeitada.
            builder.setEntityResolver((publicId, systemId) -> {
                throw new WorkflowSecurityException("Resolução de entidades externas não é permitida: " + systemId);
            });
            return builder.parse(source);
        } catch (SAXException e) {
            String message = String.valueOf(e.getMessage());
            if (message.contains("DOCTYPE")) {
                throw new WorkflowSecurityException("Declarações DOCTYPE não são permitidas (proteção contra XXE).", e);
            }
            throw new WorkflowExecutionException("XML malformado: " + message, e);
        } catch (IOException | ParserConfigurationException e) {
            throw new WorkflowExecutionException("Falha ao processar XML: " + e.getMessage(), e);
        }
    }

    private static DocumentBuilderFactory createSecureFactory() {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);
            dbf.setNamespaceAware(false);
            dbf.setIgnoringComments(true);
            return dbf;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Não foi possível configurar o parser XML seguro", e);
        }
    }
}
