package com.example.dynamicxmlrules.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.dynamicxmlrules.engine.WorkflowExecutionException;
import com.example.dynamicxmlrules.security.WorkflowSecurityException;

/**
 * Converte falhas de workflow em respostas RFC 7807 (Problem Details).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(WorkflowSecurityException.class)
    public ProblemDetail handleSecurity(WorkflowSecurityException e) {
        log.warn("Workflow rejeitado por política de segurança: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Violação de política de segurança");
        return problem;
    }

    @ExceptionHandler(WorkflowExecutionException.class)
    public ProblemDetail handleExecution(WorkflowExecutionException e) {
        log.warn("Falha na execução do workflow: {}", e.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setTitle("Erro na execução do workflow");
        return problem;
    }
}
