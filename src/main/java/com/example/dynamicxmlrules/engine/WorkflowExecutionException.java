package com.example.dynamicxmlrules.engine;

/**
 * Erro de execução de um workflow (XML inválido, expressão inválida, falha de banco etc.).
 */
public class WorkflowExecutionException extends RuntimeException {

    public WorkflowExecutionException(String message) {
        super(message);
    }

    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
