package com.example.dynamicxmlrules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.dynamicxmlrules.domain.ExecutionResult;
import com.example.dynamicxmlrules.engine.WorkflowEngine;
import com.example.dynamicxmlrules.engine.WorkflowExecutionException;

@SpringBootTest
@ActiveProfiles("test")
class WorkflowEngineIntegrationTest {

    @Autowired
    private WorkflowEngine engine;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM ofertas");
        jdbcTemplate.update("DELETE FROM books WHERE author IN ('Clarice Lispector', 'Jorge Amado')");
    }

    @Test
    void executaSampleWorkflowGerandoOfertasEPdf() throws IOException {
        ExecutionResult result = engine.execute(classpath("sample-workflow.xml"), Map.of());

        assertThat(result.workflowName()).isEqualTo("ProcessamentoOfertas");
        assertThat(result.logs()).containsExactly("Foram encontrados 4 livros na consulta.");
        assertThat(jdbcTemplate.queryForList("SELECT titulo FROM ofertas ORDER BY titulo", String.class))
                .containsExactly("Dom Casmurro", "Vidas Secas");

        assertThat(result.generatedFiles()).hasSize(1);
        Path pdf = Path.of(result.generatedFiles().get(0));
        assertThat(pdf).exists();
        byte[] header = new byte[4];
        System.arraycopy(Files.readAllBytes(pdf), 0, header, 0, 4);
        assertThat(new String(header, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void executaRamoElseQuandoNaoHaResultados() {
        String xml = """
                <workflow name="SemResultados">
                    <db-query var="livros" sql="SELECT title FROM books WHERE price &lt; :max">
                        <param name="max" value="#limite"/>
                    </db-query>
                    <if condition="#livros.size() > 0">
                        <then><log message="encontrou"/></then>
                        <else><log message="Nenhum livro abaixo de #{#limite}"/></else>
                    </if>
                </workflow>
                """;

        ExecutionResult result = engine.execute(xml, Map.of("limite", 1));

        assertThat(result.logs()).containsExactly("Nenhum livro abaixo de 1");
    }

    @Test
    void foreachExpoeItemEIndiceApenasNoEscopoLocal() {
        String xml = """
                <workflow name="Escopos">
                    <db-query var="livros" sql="SELECT title FROM books ORDER BY title"/>
                    <foreach items="#livros" var="livro" index="i">
                        <db-execute var="ultimo" sql="UPDATE books SET price = price WHERE title = :t">
                            <param name="t" value="#livro['title']"/>
                        </db-execute>
                        <log message="#{#i}:#{#livro.title}"/>
                    </foreach>
                </workflow>
                """;

        ExecutionResult result = engine.execute(xml, Map.of());

        assertThat(result.logs()).hasSize(6).first().isEqualTo("0:Clean Code");
        assertThat(result.variables()).containsKeys("livros", "ultimo").doesNotContainKeys("livro", "i");
    }

    @Test
    void importaCatalogoExternoComReadXml() throws IOException {
        ExecutionResult result = engine.execute(classpath("workflows/importar-catalogo.xml"), Map.of());

        assertThat(result.logs()).contains("Catálogo externo contém 2 livros.",
                "[1] Importado: A Hora da Estrela", "Total de livros após importação: 8");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> livros = (List<Map<String, Object>>) result.variables().get("livrosExternos");
        assertThat(livros.get(0)).containsEntry("isbn", "978-85-0000-001-1").containsEntry("autor", "Clarice Lispector");
    }

    @Test
    void falhaNoMeioDoWorkflowDesfazAlteracoes() {
        String xml = """
                <workflow name="Rollback">
                    <db-execute sql="INSERT INTO ofertas (titulo, preco) VALUES ('Temporaria', 1.00)"/>
                    <log message="#{#variavelInexistente.size()}"/>
                </workflow>
                """;

        assertThatThrownBy(() -> engine.execute(xml, Map.of())).isInstanceOf(WorkflowExecutionException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ofertas", Integer.class)).isZero();
    }

    @Test
    void tagDesconhecidaEhRejeitada() {
        assertThatThrownBy(() -> engine.execute("<workflow><shell cmd='ls'/></workflow>", Map.of()))
                .isInstanceOf(WorkflowExecutionException.class)
                .hasMessageContaining("Tag desconhecida <shell>");
    }

    private static String classpath(String path) throws IOException {
        return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
    }
}
