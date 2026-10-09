package com.example.dynamicxmlrules.handlers;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import com.example.dynamicxmlrules.domain.ExecutionContext;
import com.example.dynamicxmlrules.engine.StepHandler;
import com.example.dynamicxmlrules.engine.WorkflowEngine;
import com.example.dynamicxmlrules.engine.XmlUtils;
import com.example.dynamicxmlrules.security.SqlValidator;
import com.example.dynamicxmlrules.security.SqlValidator.SqlOperation;

/**
 * {@code <db-execute sql="INSERT ... VALUES (:a)" var="opcional"><param name="a" value="..."/></db-execute>}
 * <p>
 * Executa INSERT/UPDATE/DELETE/MERGE parametrizado. Se {@code var} for informado,
 * armazena o número de linhas afetadas.
 */
@Component
public class DbExecuteHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(DbExecuteHandler.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DbExecuteHandler(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getTagName() {
        return "db-execute";
    }

    @Override
    public void handle(Element element, ExecutionContext context, WorkflowEngine engine) {
        String sql = XmlUtils.requiredAttribute(element, "sql");
        SqlValidator.validate(sql, SqlOperation.EXECUTE);

        Map<String, Object> params = XmlUtils.parameters(element, context);
        int affectedRows = jdbcTemplate.update(sql, params);
        log.debug("db-execute afetou {} linha(s)", affectedRows);

        String var = XmlUtils.optionalAttribute(element, "var");
        if (var != null) {
            context.setVariable(var, affectedRows);
        }
    }
}
