package com.example.dynamicxmlrules.domain;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import com.example.dynamicxmlrules.engine.WorkflowExecutionException;
import com.example.dynamicxmlrules.security.SecureMethodResolver;
import com.example.dynamicxmlrules.security.WorkflowSecurityException;

/**
 * Escopo de variáveis de uma execução de workflow e avaliador SpEL em sandbox.
 * <p>
 * As variáveis ficam em uma pilha de escopos: o escopo base é global e cada
 * {@code <foreach>} empilha um escopo local para a variável de iteração.
 * Uma atribuição comum atualiza a variável no escopo onde ela já existe ou,
 * se for nova, cria-a no escopo global.
 * <p>
 * O {@link StandardEvaluationContext} é customizado para:
 * <ul>
 *   <li>rejeitar referências de tipo {@code T(...)} (TypeLocator que sempre lança exceção);</li>
 *   <li>não possuir resolvedores de construtor ({@code new ...} é bloqueado);</li>
 *   <li>permitir apenas métodos de instância seguros ({@link SecureMethodResolver});</li>
 *   <li>permitir apenas leitura de propriedades (mapas e getters de dados).</li>
 * </ul>
 */
public class ExecutionContext {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final TemplateParserContext TEMPLATE_CONTEXT = new TemplateParserContext();
    private static final Pattern VARIABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern INTEGER = Pattern.compile("-?\\d{1,18}");
    private static final Pattern DECIMAL = Pattern.compile("-?\\d+\\.\\d+");
    private static final Set<String> RESERVED_NAMES = Set.of("root", "this", "toNumber");

    private final String workflowName;
    private final Deque<Map<String, Object>> scopes = new ArrayDeque<>();
    private final List<String> logs = new ArrayList<>();
    private final List<Path> generatedFiles = new ArrayList<>();
    private final StandardEvaluationContext evaluationContext;

    private int stepCount;
    private int depth;

    public ExecutionContext(String workflowName, Map<String, ?> inputs) {
        this.workflowName = workflowName;
        this.scopes.push(new LinkedHashMap<>());
        if (inputs != null) {
            inputs.forEach(this::setVariable);
        }
        this.evaluationContext = new SandboxedEvaluationContext();
    }

    // ------------------------------------------------------------------ variáveis

    public void setVariable(String name, Object value) {
        validateName(name);
        for (Map<String, Object> scope : scopes) {
            if (scope.containsKey(name)) {
                scope.put(name, value);
                return;
            }
        }
        scopes.getLast().put(name, value);
    }

    /** Define a variável apenas no escopo corrente (usado para variáveis de iteração). */
    public void setLocalVariable(String name, Object value) {
        validateName(name);
        scopes.peek().put(name, value);
    }

    public boolean hasVariable(String name) {
        for (Map<String, Object> scope : scopes) {
            if (scope.containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    public Object getVariable(String name) {
        for (Map<String, Object> scope : scopes) {
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        return null;
    }

    public void pushScope() {
        scopes.push(new LinkedHashMap<>());
    }

    public void popScope() {
        if (scopes.size() <= 1) {
            throw new IllegalStateException("O escopo global não pode ser removido.");
        }
        scopes.pop();
    }

    /** Visão consolidada de todas as variáveis visíveis (escopos internos sobrepõem os externos). */
    public Map<String, Object> getVariables() {
        Map<String, Object> merged = new LinkedHashMap<>();
        Iterator<Map<String, Object>> fromGlobal = scopes.descendingIterator();
        while (fromGlobal.hasNext()) {
            merged.putAll(fromGlobal.next());
        }
        return merged;
    }

    private static void validateName(String name) {
        if (name == null || !VARIABLE_NAME.matcher(name).matches() || RESERVED_NAMES.contains(name)) {
            throw new WorkflowExecutionException("Nome de variável inválido: '" + name + "'");
        }
    }

    // ------------------------------------------------------------------ SpEL

    /** Avalia uma expressão SpEL (ex.: {@code #lista.size() > 0}). */
    public Object evaluate(String expression) {
        return evaluate(expression, Object.class);
    }

    public <T> T evaluate(String expression, Class<T> expectedType) {
        if (expression == null || expression.isBlank()) {
            throw new WorkflowExecutionException("Expressão vazia.");
        }
        try {
            Expression parsed = PARSER.parseExpression(expression);
            return parsed.getValue(evaluationContext, expectedType);
        } catch (ExpressionException e) {
            throw new WorkflowExecutionException("Erro ao avaliar expressão '" + expression + "': " + e.getMessage(), e);
        }
    }

    /** Interpola um texto contendo blocos {@code #{...}} (ex.: mensagens de log). */
    public String interpolate(String template) {
        Object value = evaluateTemplate(template);
        return value == null ? "" : value.toString();
    }

    /**
     * Resolve o valor de um atributo de parâmetro:
     * <ul>
     *   <li>contém {@code #{...}} → template SpEL;</li>
     *   <li>começa com {@code #} → expressão SpEL (ex.: {@code #book['price']});</li>
     *   <li>caso contrário → literal (número, booleano ou texto).</li>
     * </ul>
     */
    public Object resolveValue(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        if (value.contains("#{")) {
            return evaluateTemplate(value);
        }
        if (value.startsWith("#")) {
            return evaluate(value);
        }
        return parseLiteral(raw);
    }

    /** Converte um texto literal em número/booleano quando aplicável. */
    public static Object parseLiteral(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.strip();
        if (INTEGER.matcher(value).matches()) {
            return Long.valueOf(value);
        }
        if (DECIMAL.matcher(value).matches()) {
            return new BigDecimal(value);
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.valueOf(value);
        }
        return raw;
    }

    private Object evaluateTemplate(String template) {
        if (template == null || !template.contains("#{")) {
            return template;
        }
        try {
            return PARSER.parseExpression(template, TEMPLATE_CONTEXT).getValue(evaluationContext);
        } catch (ExpressionException e) {
            throw new WorkflowExecutionException("Erro ao interpolar '" + template + "': " + e.getMessage(), e);
        }
    }

    /** Função utilitária exposta ao SpEL como {@code #toNumber(valor)}. */
    public static BigDecimal toNumber(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        try {
            return new BigDecimal(value.toString().strip());
        } catch (NumberFormatException e) {
            throw new WorkflowExecutionException("Valor não numérico: '" + value + "'");
        }
    }

    /**
     * Contexto de avaliação em sandbox. As variáveis SpEL ({@code #nome}) são resolvidas
     * diretamente na pilha de escopos do {@link ExecutionContext}.
     */
    private final class SandboxedEvaluationContext extends StandardEvaluationContext {

        SandboxedEvaluationContext() {
            setTypeLocator(typeName -> {
                throw new WorkflowSecurityException(
                        "Referências a tipos (T(" + typeName + ")) não são permitidas em expressões.");
            });
            setConstructorResolvers(List.of((context, typeName, argumentTypes) -> {
                throw new WorkflowSecurityException(
                        "Instanciação de objetos (new " + typeName + ") não é permitida em expressões.");
            }));
            setMethodResolvers(List.of(new SecureMethodResolver()));
            setPropertyAccessors(List.of(new MapAccessor(), DataBindingPropertyAccessor.forReadOnlyAccess()));
            try {
                Method toNumber = ExecutionContext.class.getMethod("toNumber", Object.class);
                registerFunction("toNumber", toNumber);
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void setVariable(String name, Object value) {
            ExecutionContext.this.setVariable(name, value);
        }

        @Override
        public Object lookupVariable(String name) {
            if (ExecutionContext.this.hasVariable(name)) {
                return ExecutionContext.this.getVariable(name);
            }
            return super.lookupVariable(name);
        }
    }

    // ------------------------------------------------------------------ controle de execução

    public String getWorkflowName() {
        return workflowName;
    }

    public int incrementStepCount() {
        return ++stepCount;
    }

    public int getStepCount() {
        return stepCount;
    }

    public int enterBlock() {
        return ++depth;
    }

    public void exitBlock() {
        depth--;
    }

    public void addLog(String message) {
        logs.add(message);
    }

    public List<String> getLogs() {
        return Collections.unmodifiableList(logs);
    }

    public void addGeneratedFile(Path file) {
        generatedFiles.add(file);
    }

    public List<Path> getGeneratedFiles() {
        return Collections.unmodifiableList(generatedFiles);
    }
}
