package com.example.dynamicxmlrules.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xhtmlrenderer.pdf.ITextOutputDevice;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;

import com.example.dynamicxmlrules.config.RulesProperties;
import com.example.dynamicxmlrules.domain.ExecutionContext;
import com.example.dynamicxmlrules.engine.StepHandler;
import com.example.dynamicxmlrules.engine.WorkflowEngine;
import com.example.dynamicxmlrules.engine.WorkflowExecutionException;
import com.example.dynamicxmlrules.engine.XmlUtils;
import com.example.dynamicxmlrules.security.PathSandbox;
import com.example.dynamicxmlrules.security.SecureXmlParser;
import com.example.dynamicxmlrules.security.WorkflowSecurityException;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

/**
 * {@code <generate-pdf targetPath="relatorio.pdf" var="opcional"><template><![CDATA[ ...html... ]]></template></generate-pdf>}
 * <p>
 * Renderiza o template Mustache com as variáveis do contexto, converte o XHTML resultante
 * em PDF via Flying Saucer e grava o arquivo no diretório de saída ({@code rules.output-dir}).
 * <ul>
 *   <li>partials Mustache ({@code {{> arquivo}}}) são bloqueados;</li>
 *   <li>o XHTML é parseado com o parser seguro (sem DOCTYPE/entidades externas);</li>
 *   <li>recursos externos (imagens/CSS via http, file etc.) não são carregados.</li>
 * </ul>
 */
@Component
public class GeneratePdfHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(GeneratePdfHandler.class);

    private final SecureXmlParser xmlParser;
    private final RulesProperties properties;

    public GeneratePdfHandler(SecureXmlParser xmlParser, RulesProperties properties) {
        this.xmlParser = xmlParser;
        this.properties = properties;
    }

    @Override
    public String getTagName() {
        return "generate-pdf";
    }

    @Override
    public void handle(Element element, ExecutionContext context, WorkflowEngine engine) {
        String targetPath = context.interpolate(XmlUtils.requiredAttribute(element, "targetPath"));
        if (!targetPath.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new WorkflowSecurityException("O arquivo de saída deve ter extensão .pdf: " + targetPath);
        }
        Path target = PathSandbox.resolve(properties.outputDir(), targetPath);

        Element templateElement = XmlUtils.firstChild(element, "template");
        if (templateElement == null || templateElement.getTextContent().isBlank()) {
            throw new WorkflowExecutionException("<generate-pdf> requer um elemento <template> com conteúdo.");
        }

        String html = renderTemplate(templateElement.getTextContent(), context);
        writePdf(html, target);

        context.addGeneratedFile(target);
        String var = XmlUtils.optionalAttribute(element, "var");
        if (var != null) {
            context.setVariable(var, target.toString());
        }
        log.info("[{}] PDF gerado em {}", context.getWorkflowName(), target);
    }

    private static String renderTemplate(String template, ExecutionContext context) {
        MustacheFactory factory = new DefaultMustacheFactory(resourceName -> {
            throw new WorkflowSecurityException("Templates parciais não são permitidos: " + resourceName);
        });
        Mustache mustache = factory.compile(new StringReader(template.strip()), "generate-pdf");
        StringWriter writer = new StringWriter();
        mustache.execute(writer, context.getVariables());
        return writer.toString();
    }

    private void writePdf(String html, Path target) {
        Document document = xmlParser.parse(html);
        try {
            Files.createDirectories(target.getParent());
            try (OutputStream out = Files.newOutputStream(target)) {
                ITextRenderer renderer = new ITextRenderer();
                RestrictedUserAgent userAgent = new RestrictedUserAgent(renderer.getOutputDevice());
                userAgent.setSharedContext(renderer.getSharedContext());
                renderer.getSharedContext().setUserAgentCallback(userAgent);
                renderer.setDocument(document, null);
                renderer.layout();
                renderer.createPDF(out);
            }
        } catch (IOException e) {
            throw new WorkflowExecutionException("Falha ao gravar PDF: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new WorkflowExecutionException("Falha ao renderizar PDF: " + e.getMessage(), e);
        }
    }

    /** User agent que impede o Flying Saucer de buscar recursos externos (SSRF / leitura de arquivos). */
    private static final class RestrictedUserAgent extends ITextUserAgent {

        RestrictedUserAgent(ITextOutputDevice outputDevice) {
            super(outputDevice);
        }

        @Override
        protected InputStream resolveAndOpenStream(String uri) {
            if (uri != null && (uri.startsWith("jar:") || uri.startsWith("data:"))) {
                return super.resolveAndOpenStream(uri);
            }
            log.warn("Recurso externo bloqueado na geração de PDF: {}", uri);
            return null;
        }
    }
}
