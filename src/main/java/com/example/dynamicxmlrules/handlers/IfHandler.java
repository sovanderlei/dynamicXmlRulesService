package com.example.dynamicxmlrules.handlers;

import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import com.example.dynamicxmlrules.domain.ExecutionContext;
import com.example.dynamicxmlrules.engine.StepHandler;
import com.example.dynamicxmlrules.engine.WorkflowEngine;
import com.example.dynamicxmlrules.engine.WorkflowExecutionException;
import com.example.dynamicxmlrules.engine.XmlUtils;

/**
 * {@code <if condition="#x > 0"><then>...</then><else>...</else></if>}
 * <p>
 * Avalia {@code condition} via SpEL. Sem {@code <then>}/{@code <else>}, os filhos diretos
 * são executados quando a condição é verdadeira.
 */
@Component
public class IfHandler implements StepHandler {

    @Override
    public String getTagName() {
        return "if";
    }

    @Override
    public void handle(Element element, ExecutionContext context, WorkflowEngine engine) {
        String condition = XmlUtils.requiredAttribute(element, "condition");
        Boolean result = context.evaluate(condition, Boolean.class);
        if (result == null) {
            throw new WorkflowExecutionException("A condição '" + condition + "' retornou null.");
        }

        Element thenBlock = XmlUtils.firstChild(element, "then");
        Element elseBlock = XmlUtils.firstChild(element, "else");

        if (thenBlock == null && elseBlock == null) {
            if (result) {
                engine.executeChildren(element, context);
            }
            return;
        }

        for (Element child : XmlUtils.childElements(element)) {
            String tag = child.getTagName();
            if (!"then".equals(tag) && !"else".equals(tag)) {
                throw new WorkflowExecutionException(
                        "Ao usar <then>/<else>, <if> não pode conter outros filhos (encontrado <" + tag + ">).");
            }
        }

        Element branch = result ? thenBlock : elseBlock;
        if (branch != null) {
            engine.executeChildren(branch, context);
        }
    }
}
