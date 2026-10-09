package com.example.dynamicxmlrules.security;

import java.util.Locale;
import java.util.Set;

/**
 * Validação estática de SQL antes da execução. Complementa (não substitui) o uso
 * obrigatório de parâmetros vinculados via {@code NamedParameterJdbcTemplate}.
 */
public final class SqlValidator {

    public enum SqlOperation {
        QUERY("<db-query>", Set.of("SELECT", "WITH")),
        EXECUTE("<db-execute>", Set.of("INSERT", "UPDATE", "DELETE", "MERGE"));

        private final String tag;
        private final Set<String> allowedCommands;

        SqlOperation(String tag, Set<String> allowedCommands) {
            this.tag = tag;
            this.allowedCommands = allowedCommands;
        }
    }

    private SqlValidator() {
    }

    public static void validate(String sql, SqlOperation operation) {
        if (sql == null || sql.isBlank()) {
            throw new WorkflowSecurityException("SQL vazio não é permitido.");
        }
        if (sql.indexOf(';') >= 0) {
            throw new WorkflowSecurityException("SQL contém ';' - múltiplos comandos não são permitidos.");
        }
        if (sql.contains("--") || sql.contains("/*")) {
            throw new WorkflowSecurityException("Comentários SQL não são permitidos.");
        }
        String command = sql.strip().split("\\s+", 2)[0].toUpperCase(Locale.ROOT);
        if (!operation.allowedCommands.contains(command)) {
            throw new WorkflowSecurityException("Comando SQL '" + command + "' não é permitido em "
                    + operation.tag + ". Permitidos: " + operation.allowedCommands);
        }
    }
}
