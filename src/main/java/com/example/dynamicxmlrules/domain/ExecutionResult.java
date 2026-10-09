package com.example.dynamicxmlrules.domain;

import java.nio.file.Path;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resultado serializável de uma execução de workflow.
 */
public record ExecutionResult(
        String workflowName,
        long durationMs,
        int stepsExecuted,
        List<String> logs,
        List<String> generatedFiles,
        Map<String, Object> variables) {

    public static ExecutionResult from(ExecutionContext context, long durationMs) {
        Map<String, Object> variables = new LinkedHashMap<>();
        context.getVariables().forEach((name, value) -> variables.put(name, toSerializable(value)));
        return new ExecutionResult(
                context.getWorkflowName(),
                durationMs,
                context.getStepCount(),
                List.copyOf(context.getLogs()),
                context.getGeneratedFiles().stream().map(Path::toString).toList(),
                variables);
    }

    /** Converte valores do contexto (ex.: DOM, Paths) em tipos seguros para JSON. */
    private static Object toSerializable(Object value) {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof Temporal || value instanceof Date) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((k, v) -> copy.put(String.valueOf(k), toSerializable(v)));
            return copy;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            collection.forEach(item -> copy.add(toSerializable(item)));
            return copy;
        }
        return value.toString();
    }
}
