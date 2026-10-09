package com.example.dynamicxmlrules.handlers;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import com.example.dynamicxmlrules.domain.ExecutionContext;
import com.example.dynamicxmlrules.engine.StepHandler;
import com.example.dynamicxmlrules.engine.WorkflowEngine;
import com.example.dynamicxmlrules.engine.XmlUtils;

/**
 * {@code <log message="Total: #{#lista.size()}" level="info|warn|error|debug"/>}
 * <p>
 * Escreve a mensagem interpolada no log da aplicação e no resultado da execução.
 */
@Component
public class LogHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger("workflow");

    @Override
    public String getTagName() {
        return "log";
    }

    @Override
    public void handle(Element element, ExecutionContext context, WorkflowEngine engine) {
        String message = context.interpolate(XmlUtils.requiredAttribute(element, "message"));
        String level = String.valueOf(XmlUtils.optionalAttribute(element, "level")).toLowerCase(Locale.ROOT);
        String workflow = context.getWorkflowName();

        switch (level) {
            case "debug" -> log.debug("[{}] {}", workflow, message);
            case "warn" -> log.warn("[{}] {}", workflow, message);
            case "error" -> log.error("[{}] {}", workflow, message);
            default -> log.info("[{}] {}", workflow, message);
        }
        context.addLog(message);
    }
}
