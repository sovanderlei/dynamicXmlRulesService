package com.example.dynamicxmlrules.security;

import java.util.List;
import java.util.Set;

import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.spel.support.DataBindingMethodResolver;

/**
 * {@link MethodResolver} restritivo para o SpEL: permite apenas métodos de instância
 * (nunca estáticos) e bloqueia chamadas sobre tipos sensíveis
 * (reflexão, class loaders, processos, threads, I/O, rede).
 */
public class SecureMethodResolver implements MethodResolver {

    private static final Set<Class<?>> BLOCKED_TYPES = Set.of(
            Class.class, ClassLoader.class, Runtime.class, ProcessBuilder.class,
            Process.class, Thread.class, System.class, Module.class);

    private static final Set<String> BLOCKED_PACKAGES = Set.of(
            "java.lang.reflect", "java.lang.invoke", "java.io", "java.nio.file", "java.net");

    private static final Set<String> BLOCKED_METHODS = Set.of(
            "getClass", "getClassLoader", "wait", "notify", "notifyAll");

    private final MethodResolver delegate = DataBindingMethodResolver.forInstanceMethodInvocation();

    @Override
    public MethodExecutor resolve(EvaluationContext context, Object targetObject, String name,
                                  List<TypeDescriptor> argumentTypes) throws AccessException {
        if (targetObject == null) {
            return null;
        }
        if (BLOCKED_METHODS.contains(name)) {
            throw new WorkflowSecurityException("Chamada ao método '" + name + "' não é permitida em expressões.");
        }
        if (targetObject instanceof Class<?>) {
            throw new WorkflowSecurityException("Acesso a java.lang.Class não é permitido em expressões.");
        }
        Class<?> type = targetObject.getClass();
        for (Class<?> blocked : BLOCKED_TYPES) {
            if (blocked.isAssignableFrom(type)) {
                throw new WorkflowSecurityException("Acesso ao tipo " + blocked.getName() + " não é permitido em expressões.");
            }
        }
        String packageName = type.getPackageName();
        for (String blockedPackage : BLOCKED_PACKAGES) {
            if (packageName.startsWith(blockedPackage)) {
                throw new WorkflowSecurityException("Acesso a tipos do pacote " + blockedPackage + " não é permitido em expressões.");
            }
        }
        return delegate.resolve(context, targetObject, name, argumentTypes);
    }
}
