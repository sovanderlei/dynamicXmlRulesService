package com.example.dynamicxmlrules.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.w3c.dom.Element;

import com.example.dynamicxmlrules.config.RulesProperties;
import com.example.dynamicxmlrules.domain.ExecutionContext;
import com.example.dynamicxmlrules.engine.StepHandler;
import com.example.dynamicxmlrules.engine.WorkflowEngine;
import com.example.dynamicxmlrules.engine.WorkflowExecutionException;
import com.example.dynamicxmlrules.engine.XmlUtils;

/**
 * {@code <foreach items="#lista" var="item" index="i">...</foreach>}
 * <p>
 * Itera sobre uma coleção (Collection, array ou valores de um Map) expondo o item corrente
 * — e opcionalmente o índice — como variáveis locais ao corpo do laço.
 */
@Component
public class ForEachHandler implements StepHandler {

    private final RulesProperties properties;

    public ForEachHandler(RulesProperties properties) {
        this.properties = properties;
    }

    @Override
    public String getTagName() {
        return "foreach";
    }

    @Override
    public void handle(Element element, ExecutionContext context, WorkflowEngine engine) {
        String itemsExpression = XmlUtils.requiredAttribute(element, "items");
        String var = XmlUtils.requiredAttribute(element, "var");
        String indexVar = XmlUtils.optionalAttribute(element, "index");

        List<?> items = toList(context.evaluate(itemsExpression), itemsExpression);
        if (items.size() > properties.maxIterations()) {
            throw new WorkflowExecutionException("Coleção '" + itemsExpression + "' possui " + items.size()
                    + " itens; o limite é " + properties.maxIterations() + ".");
        }

        context.pushScope();
        try {
            for (int i = 0; i < items.size(); i++) {
                context.setLocalVariable(var, items.get(i));
                if (indexVar != null) {
                    context.setLocalVariable(indexVar, i);
                }
                engine.executeChildren(element, context);
            }
        } finally {
            context.popScope();
        }
    }

    private static List<?> toList(Object value, String expression) {
        if (value instanceof Collection<?> collection) {
            // cópia defensiva: o corpo do laço pode alterar a coleção original
            return new ArrayList<>(collection);
        }
        if (value instanceof Map<?, ?> map) {
            return new ArrayList<>(map.values());
        }
        if (value != null && value.getClass().isArray()) {
            return Arrays.asList(ObjectUtils.toObjectArray(value));
        }
        throw new WorkflowExecutionException("A expressão '" + expression + "' não resultou em uma coleção iterável"
                + (value == null ? " (valor null)." : " (tipo " + value.getClass().getSimpleName() + ")."));
    }
}
