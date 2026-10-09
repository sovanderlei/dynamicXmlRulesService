package com.example.dynamicxmlrules.security;

import com.example.dynamicxmlrules.engine.WorkflowExecutionException;

/**
 * Violação de política de segurança detectada durante o parsing ou a execução de um workflow
 * (XXE, uso de {@code T(...)} no SpEL, SQL com múltiplos comandos, path traversal etc.).
 */
public class WorkflowSecurityException extends WorkflowExecutionException {

    public WorkflowSecurityException(String message) {
        super(message);
    }

    public WorkflowSecurityException(String message, Throwable cause) {
        super(message, cause);
    }
}
