package com.example.dynamicxmlrules.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limites e diretórios de sandbox do motor de regras.
 *
 * @param inputDir        diretório base para leituras de {@code <read-xml>}
 * @param outputDir       diretório base para escritas de {@code <generate-pdf>}
 * @param maxSteps        número máximo de passos executados por workflow
 * @param maxDepth        profundidade máxima de aninhamento de blocos
 * @param maxIterations   número máximo de itens processados por {@code <foreach>}
 * @param maxWorkflowSize tamanho máximo (em caracteres) de um workflow recebido via API
 */
@ConfigurationProperties(prefix = "rules")
public record RulesProperties(
        @DefaultValue("./samples/xml") Path inputDir,
        @DefaultValue("./output") Path outputDir,
        @DefaultValue("10000") int maxSteps,
        @DefaultValue("50") int maxDepth,
        @DefaultValue("5000") int maxIterations,
        @DefaultValue("1048576") int maxWorkflowSize) {
}
