package com.example.dynamicxmlrules;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class WorkflowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void executaWorkflowRecebidoNoCorpoComVariaveisDeEntrada() throws Exception {
        String xml = """
                <workflow name="Api">
                    <db-query var="livros" sql="SELECT title, price FROM books WHERE price &lt; :max ORDER BY price">
                        <param name="max" value="#max"/>
                    </db-query>
                    <log message="#{#livros.size()} livro(s) abaixo de #{#max}"/>
                </workflow>
                """;

        mockMvc.perform(post("/api/workflows/execute").param("max", "10")
                        .contentType(MediaType.APPLICATION_XML).content(xml))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowName").value("Api"))
                .andExpect(jsonPath("$.logs[0]").value("2 livro(s) abaixo de 10"))
                .andExpect(jsonPath("$.variables.livros[0].title").value("Vidas Secas"));
    }

    @Test
    void retorna400ParaViolacaoDeSeguranca() throws Exception {
        mockMvc.perform(post("/api/workflows/execute").contentType(MediaType.APPLICATION_XML)
                        .content("<workflow><log message=\"#{T(java.lang.Runtime).getRuntime()}\"/></workflow>"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Violação de política de segurança"));
    }

    @Test
    void retorna422ParaErroDeExecucao() throws Exception {
        mockMvc.perform(post("/api/workflows/execute").contentType(MediaType.APPLICATION_XML)
                        .content("<workflow><foreach items=\"#naoExiste\" var=\"x\"/></workflow>"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void listaTagsSuportadas() throws Exception {
        mockMvc.perform(get("/api/workflows/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7));
    }
}
