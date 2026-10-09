# Prompt de Inicialização de Projeto: DynamicXmlRulesService

Atue como um Engenheiro de Software Senior especializado em Java e Spring Boot. Crie um projeto completo, funcional e bem estruturado para o microserviço **DynamicXmlRulesService** seguindo as especificações abaixo.

---

## 🎯 Objetivo do Projeto

Desenvolver o microserviço **DynamicXmlRulesService** em **Java com Spring Boot**, atuando como um **Motor de Execução de Regras de Negócio Dinâmicas baseadas em XML (XML Interpreter Engine)**.

O motor deve ler arquivos XML contendo lógica procedural (consultas a banco, loops, condicionais, leitura de XMLs externos, inserção de dados e geração de relatórios em PDF) e executá-los com isolamento e segurança.

---

## 🛠️ Tech Stack & Requisitos Técnicos

- **Nome do Projeto / Repositório:** `DynamicXmlRulesService` (`dynamic-xml-rules-service`)
- **Linguagem:** Java 17+
- **Framework:** Spring Boot 3.x (Spring Data JDBC / NamedParameterJdbcTemplate)
- **Motor de Expressões:** Spring Expression Language (SpEL)
- **Geração de PDF:** Flying Saucer (`flying-saucer-pdf-openpdf`) + Mustache (`compiler`)
- **XML Parser:** DOM (`javax.xml.parsers.DocumentBuilderFactory`) com configurações de segurança ativas.
- **Banco de Dados:** H2 (para testes em memória e desenvolvimento local).

---

## 🔒 Requisitos Mandatórios de Segurança

1. **Proteção Rigorosa contra XXE (XML External Entity):**
   - Desabilitar chamadas `DOCTYPE`, entidades externas gerais e de parâmetros no `DocumentBuilderFactory`.
   - Desabilitar carregamento de DTDs externas e esquemas externos (`XMLConstants.ACCESS_EXTERNAL_DTD` e `ACCESS_EXTERNAL_SCHEMA` definidos como vazios).

2. **Hardening e Sandboxing do SpEL:**
   - Customizar o `StandardEvaluationContext` dentro do `ExecutionContext`.
   - Sobrescrever o `TypeLocator` para disparar uma exceção caso haja tentativa de resolver tipos estáticos usando a sintaxe `T(...)` (ex: `T(java.lang.Runtime)`).

3. **Prevenção de SQL Injection:**
   - Utilizar exclusivamente `NamedParameterJdbcTemplate` com vinculação de parâmetros.
   - Validar todas as queries SQL antes da execução para bloquear comandos múltiplos (uso de `;`).

---

## 📐 Arquitetura & Design Patterns

O projeto deve utilizar o padrão **Interpreter / Chain of Responsibility**:

- `ExecutionContext`: Objeto que mantém o escopo de variáveis e fornece avaliação segura com SpEL.
- `StepHandler` (Interface): Contém os métodos `String getTagName()` e `void handle(Element element, ExecutionContext context, WorkflowEngine engine)`.
- `WorkflowEngine` (Service): Injeta todos os `StepHandler` via Spring em um `Map<String, StepHandler>` e percorre os nós do XML executando o handler correto.

### Handlers a serem implementados:

1. `db-query`: Executa `SELECT` parametrizado e guarda a lista de resultados em uma variável.
2. `db-execute`: Executa `INSERT`/`UPDATE`/`DELETE` parametrizado.
3. `read-xml`: Lê um arquivo XML do sistema de arquivos e armazena seu conteúdo ou representação no contexto.
4. `if`: Avalia a condição no atributo `condition` via SpEL. Executa os filhos da tag `<then>` se verdadeiro, ou `<else>` se falso.
5. `foreach`: Itera sobre uma `Collection` armazenada no contexto, expondo o item atual em uma variável local configurada.
6. `generate-pdf`: Lê um template HTML/Mustache contido na tag, substitui as variáveis do contexto e gera um arquivo PDF no caminho especificado via Flying Saucer.
7. `log`: Escreve mensagens no console/log contendo variáveis interpoladas.

---

## 📂 Estrutura de Pacotes do Projeto

```text
src/main/java/com/example/dynamicxmlrules/
├── DynamicXmlRulesServiceApplication.java
├── config/
│   └── JdbcConfig.java
├── domain/
│   └── ExecutionContext.java
├── engine/
│   ├── StepHandler.java
│   └── WorkflowEngine.java
├── handlers/
│   ├── DbExecuteHandler.java
│   ├── DbQueryHandler.java
│   ├── ForEachHandler.java
│   ├── GeneratePdfHandler.java
│   ├── IfHandler.java
│   ├── LogHandler.java
│   └── ReadXmlHandler.java
└── controller/
    └── WorkflowController.java


## 📂 criar o xml abaixo na pasta
src/main/resources/sample-workflow.xml
```

<?xml version="1.0" encoding="UTF-8"?>
<workflow name="ProcessamentoOfertas">

    <db-query var="livrosBaratos" sql="SELECT title, price, author FROM books WHERE price < :maxPrice">
        <param name="maxPrice" value="15.00"/>
    </db-query>

    <log message="Foram encontrados #{#livrosBaratos.size()} livros na consulta."/>

    <if condition="#livrosBaratos.size() > 0">
        <then>
            <foreach items="#livrosBaratos" var="book">
                <if condition="#book['price'] < 10.0">
                    <then>
                        <db-execute sql="INSERT INTO ofertas (titulo, preco) VALUES (:titulo, :preco)">
                            <param name="titulo" value="#book['title']"/>
                            <param name="preco" value="#book['price']"/>
                        </db-execute>
                    </then>
                </if>
            </foreach>

            <generate-pdf targetPath="saida_relatorio.pdf">
                <template><![CDATA[
                    <html>
                    <body>
                        <h1>Relatório de Livros em Promoção</h1>
                        <ul>
                            {{#livrosBaratos}}
                            <li><b>{{title}}</b> - R$ {{price}} ({{author}})</li>
                            {{/livrosBaratos}}
                        </ul>
                    </body>
                    </html>
                ]]></template>
            </generate-pdf>
        </then>
        <else>
            <log message="Nenhum livro encontrado para o critério informado."/>
        </else>
    </if>

</workflow>
```
