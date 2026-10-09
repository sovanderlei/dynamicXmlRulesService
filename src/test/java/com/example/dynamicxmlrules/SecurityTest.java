package com.example.dynamicxmlrules;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.example.dynamicxmlrules.engine.WorkflowEngine;
import com.example.dynamicxmlrules.engine.WorkflowExecutionException;
import com.example.dynamicxmlrules.security.WorkflowSecurityException;

@SpringBootTest
@ActiveProfiles("test")
class SecurityTest {

    @Autowired
    private WorkflowEngine engine;

    @Test
    void bloqueiaXxeComDoctype() {
        String xml = """
                <?xml version="1.0"?>
                <!DOCTYPE workflow [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
                <workflow name="xxe"><log message="&xxe;"/></workflow>
                """;

        assertThatThrownBy(() -> engine.execute(xml, Map.of()))
                .isInstanceOf(WorkflowSecurityException.class)
                .hasMessageContaining("DOCTYPE");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "T(java.lang.Runtime).getRuntime().exec('calc')",
            "T(java.lang.System).getProperty('user.home')",
            "new java.io.File('x').exists()"
    })
    void bloqueiaReferenciasDeTipoNoSpel(String expression) {
        assertThatThrownBy(() -> run("<if condition=\"" + expression + "\"><log message='x'/></if>"))
                .isInstanceOf(WorkflowSecurityException.class)
                .hasMessageContaining("permitid");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "''.getClass().getName() != null",
            "''.class.name != null",
            "#nome.getClass().forName('java.lang.Runtime') != null"
    })
    void bloqueiaReflexaoNoSpel(String expression) {
        assertThatThrownBy(() -> engine.execute(
                "<workflow><if condition=\"" + expression + "\"><log message='x'/></if></workflow>",
                Map.of("nome", "abc")))
                .isInstanceOf(WorkflowExecutionException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM books; DROP TABLE books",
            "SELECT * FROM books -- comentario",
            "DELETE FROM books"
    })
    void bloqueiaSqlPerigosoEmDbQuery(String sql) {
        assertThatThrownBy(() -> run("<db-query var='x' sql='" + sql + "'/>"))
                .isInstanceOf(WorkflowSecurityException.class);
    }

    @Test
    void bloqueiaDdlEmDbExecute() {
        assertThatThrownBy(() -> run("<db-execute sql='DROP TABLE books'/>"))
                .isInstanceOf(WorkflowSecurityException.class)
                .hasMessageContaining("DROP");
    }

    @ParameterizedTest
    @ValueSource(strings = {"../fora.pdf", "/tmp/abs.pdf", "relatorio.exe"})
    void bloqueiaEscritaDePdfForaDoSandbox(String target) {
        assertThatThrownBy(() -> run("<generate-pdf targetPath='" + target + "'><template>&lt;html/&gt;</template></generate-pdf>"))
                .isInstanceOf(WorkflowSecurityException.class);
    }

    @Test
    void bloqueiaLeituraDeXmlForaDoSandbox() {
        assertThatThrownBy(() -> run("<read-xml var='pom' path='../../pom.xml'/>"))
                .isInstanceOf(WorkflowSecurityException.class);
    }

    @Test
    void bloqueiaPartialsMustache() {
        assertThatThrownBy(() -> run("<generate-pdf targetPath='p.pdf'><template>&lt;html&gt;{{> ../../pom.xml}}&lt;/html&gt;</template></generate-pdf>"))
                .isInstanceOf(WorkflowExecutionException.class);
    }

    private void run(String steps) {
        engine.execute("<workflow name='teste'>" + steps + "</workflow>", Map.of());
    }
}
