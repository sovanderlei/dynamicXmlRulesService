package com.example.dynamicxmlrules.handlers;

import java.util.List;
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
 * {@code <db-query var="x" sql="SELECT ... WHERE c = :p"><param name="p" value="..."/></db-query>}
 * <p>
 * Executa um SELECT parametrizado e guarda a lista de linhas (cada linha um {@code Map}) em {@code var}.
 */
@Component
public class DbQueryHandler implements StepHandler {

    private static final Logger log = LoggerFactory.getLogger(DbQueryHandler.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DbQueryHandler(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String getTagName() {
        return "db-query";
    }

    @Override
    public void handle(Element element, ExecutionContext context, WorkflowEngine engine) {
        String var = XmlUtils.requiredAttribute(element, "var");
        String sql = XmlUtils.requiredAttribute(element, "sql");
        SqlValidator.validate(sql, SqlOperation.QUERY);

        Map<String, Object> params = XmlUtils.parameters(element, context);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        context.setVariable(var, rows);
        log.debug("db-query '{}' retornou {} linha(s)", var, rows.size());
    }
}
