package com.example.dynamicxmlrules.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.dynamicxmlrules.config.RulesProperties;
import com.example.dynamicxmlrules.domain.ExecutionContext;
import com.example.dynamicxmlrules.domain.ExecutionResult;
import com.example.dynamicxmlrules.engine.WorkflowEngine;

/**
 * API REST para execução de workflows XML.
 * <ul>
 *   <li>{@code POST /api/workflows/execute} — corpo XML; query params viram variáveis de entrada;</li>
 *   <li>{@code POST /api/workflows/samples/{name}} — executa um workflow de exemplo do classpath;</li>
 *   <li>{@code GET  /api/workflows/tags} — lista as tags suportadas.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private static final Pattern SAMPLE_NAME = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final WorkflowEngine engine;
    private final RulesProperties properties;

    public WorkflowController(WorkflowEngine engine, RulesProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    @PostMapping(value = "/execute",
            consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, MediaType.TEXT_PLAIN_VALUE},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ExecutionResult execute(@RequestBody String workflowXml,
                                   @RequestParam Map<String, String> inputs) {
        if (workflowXml.length() > properties.maxWorkflowSize()) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Workflow excede o tamanho máximo de " + properties.maxWorkflowSize() + " caracteres.");
        }
        return engine.execute(workflowXml, toInputs(inputs));
    }

    @PostMapping(value = "/samples/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ExecutionResult executeSample(@PathVariable String name,
                                         @RequestParam Map<String, String> inputs) throws IOException {
        if (!SAMPLE_NAME.matcher(name).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome de workflow inválido.");
        }
        ClassPathResource resource = "sample-workflow".equals(name)
                ? new ClassPathResource("sample-workflow.xml")
                : new ClassPathResource("workflows/" + name + ".xml");
        if (!resource.exists()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow de exemplo não encontrado: " + name);
        }
        String xml = resource.getContentAsString(StandardCharsets.UTF_8);
        return engine.execute(xml, toInputs(inputs));
    }

    @GetMapping("/tags")
    public Set<String> supportedTags() {
        return engine.getSupportedTags();
    }

    private static Map<String, Object> toInputs(Map<String, String> params) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        params.forEach((key, value) -> inputs.put(key, ExecutionContext.parseLiteral(value)));
        return inputs;
    }
}
