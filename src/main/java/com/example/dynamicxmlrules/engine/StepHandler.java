package com.example.dynamicxmlrules.engine;

import org.w3c.dom.Element;

import com.example.dynamicxmlrules.domain.ExecutionContext;

/**
 * Handler de uma tag do workflow (padrão Interpreter / Chain of Responsibility).
 * Cada implementação registrada como bean Spring é descoberta automaticamente pelo
 * {@link WorkflowEngine} através de {@link #getTagName()}.
 */
public interface StepHandler {

    /** Nome da tag XML tratada por este handler (ex.: {@code db-query}). */
    String getTagName();

    /**
     * Executa o passo.
     *
     * @param element elemento XML do passo
     * @param context escopo de variáveis e avaliador SpEL da execução
     * @param engine  motor, para handlers que executam blocos filhos ({@code if}, {@code foreach})
     */
    void handle(Element element, ExecutionContext context, WorkflowEngine engine);
}
