package com.example.dynamicxmlrules.handlers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.xml.XMLConstants;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathFactoryConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.example.dynamicxmlrules.config.RulesProperties;
import com.example.dynamicxmlrules.domain.ExecutionContext;
import com.example.dynamicxmlrules.engine.StepHandler;
import com.example.dynamicxmlrules.engine.WorkflowEngine;
import com.example.dynamicxmlrules.engine.WorkflowExecutionException;
import com.example.dynamicxmlrules.engine.XmlUtils;
import com.example.dynamicxmlrules.security.PathSandbox;
import com.example.dynamicxmlrules.security.SecureXmlParser;

/**
 * {@code <read-xml path="arquivo.xml" var="dados" xpath="/raiz/item" mode="map|text|dom"/>}
 * <p>
 * Lê um XML do diretório de entrada configurado ({@code rules.input-dir}) com o parser seguro.
 * <ul>
 *   <li>com {@code xpath}: armazena uma lista com cada nó selecionado convertido em Map/texto;</li>
 *   <li>{@code mode="map"} (padrão): armazena o elemento raiz convertido em Map;</li>
 *   <li>{@code mode="text"}: armazena o conteúdo bruto do arquivo;</li>
 *   <li>{@code mode="dom"}: armazena o {@link Document}.</li>
 * </ul>
 */
@Component
public class ReadXmlHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(ReadXmlHandler.class);

    private final SecureXmlParser xmlParser;
    private final RulesProperties properties;

    public ReadXmlHandler(SecureXmlParser xmlParser, RulesProperties properties) {
        this.xmlParser = xmlParser;
        this.properties = properties;
    }

    @Override
    public String getTagName() {
        return "read-xml";
    }

    @Override
    public void handle(Element element, ExecutionContext context, WorkflowEngine engine) {
        String var = XmlUtils.requiredAttribute(element, "var");
        String relativePath = context.interpolate(XmlUtils.requiredAttribute(element, "path"));
        String xpath = XmlUtils.optionalAttribute(element, "xpath");
        String mode = String.valueOf(XmlUtils.optionalAttribute(element, "mode")).toLowerCase(Locale.ROOT);

        Path file = PathSandbox.resolve(properties.inputDir(), relativePath);
        if (!Files.isRegularFile(file)) {
            throw new WorkflowExecutionException("Arquivo XML não encontrado: " + relativePath);
        }

        Object value;
        if ("text".equals(mode)) {
            value = readText(file);
        } else {
            Document document = xmlParser.parse(file);
            if (xpath != null) {
                value = select(document, xpath);
            } else if ("dom".equals(mode)) {
                value = document;
            } else {
                value = XmlUtils.toValue(document.getDocumentElement());
            }
        }
        context.setVariable(var, value);
        log.debug("read-xml '{}' carregado em '{}'", relativePath, var);
    }

    private static String readText(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new WorkflowExecutionException("Falha ao ler arquivo: " + file.getFileName(), e);
        }
    }

    private static List<Object> select(Document document, String expression) {
        try {
            XPathFactory factory = XPathFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            XPath xPath = factory.newXPath();
            NodeList nodes = (NodeList) xPath.evaluate(expression, document, XPathConstants.NODESET);
            List<Object> result = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                result.add(node instanceof Element el ? XmlUtils.toValue(el) : node.getTextContent());
            }
            return result;
        } catch (XPathExpressionException | XPathFactoryConfigurationException e) {
            throw new WorkflowExecutionException("XPath inválido '" + expression + "': " + e.getMessage(), e);
        }
    }
}
