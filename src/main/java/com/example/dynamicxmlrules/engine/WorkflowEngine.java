package com.example.dynamicxmlrules.engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.example.dynamicxmlrules.config.RulesProperties;
import com.example.dynamicxmlrules.domain.ExecutionContext;
import com.example.dynamicxmlrules.domain.ExecutionResult;
import com.example.dynamicxmlrules.security.SecureXmlParser;

/**
 * Interpretador de workflows XML. Todos os {@link StepHandler} registrados no Spring são
 * indexados por nome de tag; o motor percorre a árvore DOM e delega cada elemento ao
 * handler correspondente. A execução inteira ocorre em uma única transação: qualquer
 * falha desfaz as alterações de banco feitas pelo workflow.
 */
@Service
public class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final Map<String, StepHandler> handlers;
    private final SecureXmlParser xmlParser;
    private final TransactionTemplate transactionTemplate;
    private final RulesProperties properties;

    public WorkflowEngine(List<StepHandler> stepHandlers,
                          SecureXmlParser xmlParser,
                          TransactionTemplate transactionTemplate,
                          RulesProperties properties) {
        Map<String, StepHandler> byTag = new HashMap<>();
        for (StepHandler handler : stepHandlers) {
            StepHandler previous = byTag.putIfAbsent(handler.getTagName(), handler);
            if (previous != null) {
                throw new IllegalStateException("Tag <" + handler.getTagName() + "> registrada por mais de um handler: "
                        + previous.getClass().getName() + " e " + handler.getClass().getName());
            }
        }
        this.handlers = Map.copyOf(byTag);
        this.xmlParser = xmlParser;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        log.info("WorkflowEngine inicializado com as tags: {}", getSupportedTags());
    }

    public Set<String> getSupportedTags() {
        return new TreeSet<>(handlers.keySet());
    }

    /** Faz o parsing seguro do XML e executa o workflow. */
    public ExecutionResult execute(String workflowXml, Map<String, ?> inputs) {
        Document document = xmlParser.parse(workflowXml);
        Element root = document.getDocumentElement();
        if (!"workflow".equals(root.getTagName())) {
            throw new WorkflowExecutionException("O elemento raiz deve ser <workflow>, encontrado <" + root.getTagName() + ">.");
        }
        String name = root.hasAttribute("name") ? root.getAttribute("name") : "workflow-sem-nome";
        ExecutionContext context = new ExecutionContext(name, inputs);

        log.info("Iniciando workflow '{}'", name);
        long start = System.nanoTime();
        transactionTemplate.executeWithoutResult(status -> executeChildren(root, context));
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        log.info("Workflow '{}' concluído em {} ms ({} passos)", name, durationMs, context.getStepCount());

        return ExecutionResult.from(context, durationMs);
    }

    /** Executa, em ordem, todos os elementos filhos de {@code parent}. */
    public void executeChildren(Element parent, ExecutionContext context) {
        if (context.enterBlock() > properties.maxDepth()) {
            throw new WorkflowExecutionException("Profundidade máxima de aninhamento excedida (" + properties.maxDepth() + ").");
        }
        try {
            for (Element child : XmlUtils.childElements(parent)) {
                executeStep(child, context);
            }
        } finally {
            context.exitBlock();
        }
    }

    /** Executa um único elemento delegando ao handler da tag. */
    public void executeStep(Element element, ExecutionContext context) {
        String tag = element.getTagName();
        StepHandler handler = handlers.get(tag);
        if (handler == null) {
            throw new WorkflowExecutionException("Tag desconhecida <" + tag + ">. Tags suportadas: " + getSupportedTags());
        }
        if (context.incrementStepCount() > properties.maxSteps()) {
            throw new WorkflowExecutionException("Limite máximo de passos excedido (" + properties.maxSteps() + ").");
        }
        try {
            handler.handle(element, context, this);
        } catch (WorkflowExecutionException e) {
            throw e;
        } catch (DataAccessException e) {
            throw new WorkflowExecutionException("Erro de banco de dados em <" + tag + ">: "
                    + e.getMostSpecificCause().getMessage(), e);
        } catch (RuntimeException e) {
            throw new WorkflowExecutionException("Erro ao executar <" + tag + ">: " + e.getMessage(), e);
        }
    }
}
