# DynamicXmlRulesService

Motor de Regras de Negócio Dinâmico em Java & Spring Boot — um **interpretador de workflows XML** que executa lógica procedural (consultas a banco, laços, condicionais, leitura de XMLs externos, inserções e geração de relatórios PDF) com isolamento e segurança.

## Stack

| Item | Tecnologia |
|---|---|
| Linguagem | Java 17+ |
| Framework | Spring Boot 3.3 (`NamedParameterJdbcTemplate`) |
| Expressões | Spring Expression Language (SpEL) em sandbox |
| PDF | Flying Saucer (`flying-saucer-pdf-openpdf`) + Mustache |
| XML | DOM (`DocumentBuilderFactory`) endurecido contra XXE |
| Banco | H2 em memória |

## Executando

```bash
mvn spring-boot:run
```

```bash
# Executa o workflow de exemplo (src/main/resources/sample-workflow.xml)
curl -X POST http://localhost:8080/api/workflows/samples/sample-workflow

# Executa o exemplo de importação via <read-xml>
curl -X POST http://localhost:8080/api/workflows/samples/importar-catalogo

# Executa um workflow enviado no corpo; query params viram variáveis (#max)
curl -X POST "http://localhost:8080/api/workflows/execute?max=10" \
     -H "Content-Type: application/xml" --data-binary @meu-workflow.xml

# Lista as tags suportadas
curl http://localhost:8080/api/workflows/tags
```

O PDF é gravado em `./output/` e o console do H2 fica em `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:rulesdb`).

> **Rede corporativa / antivírus com inspeção TLS:** se o Maven falhar com `PKIX path building failed`, use o repositório de certificados do Windows: `mvn -Djavax.net.ssl.trustStoreType=Windows-ROOT ...`

## Arquitetura (Interpreter / Chain of Responsibility)

```
WorkflowController ──► WorkflowEngine ──► SecureXmlParser (DOM sem XXE)
                            │
                            ├─ Map<tag, StepHandler>  (todos os beans StepHandler injetados)
                            └─ ExecutionContext        (pilha de escopos + SpEL em sandbox)
```

- **`ExecutionContext`** — escopos de variáveis (global + local por `foreach`) e avaliação SpEL com `StandardEvaluationContext` customizado.
- **`StepHandler`** — `getTagName()` + `handle(Element, ExecutionContext, WorkflowEngine)`.
- **`WorkflowEngine`** — indexa os handlers por tag, percorre o DOM e executa tudo em **uma transação** (falha ⇒ rollback).

### Tags

| Tag | Atributos | Descrição |
|---|---|---|
| `db-query` | `var`, `sql` + `<param>` | `SELECT` parametrizado; guarda `List<Map>` em `var` |
| `db-execute` | `sql`, `var?` + `<param>` | `INSERT/UPDATE/DELETE/MERGE`; `var` recebe linhas afetadas |
| `read-xml` | `path`, `var`, `xpath?`, `mode?` (`map`/`text`/`dom`) | Lê XML de `rules.input-dir` |
| `if` | `condition` + `<then>`/`<else>` | Condicional SpEL |
| `foreach` | `items`, `var`, `index?` | Itera Collection/array/Map com variáveis locais |
| `generate-pdf` | `targetPath`, `var?` + `<template>` | Mustache → XHTML → PDF em `rules.output-dir` |
| `log` | `message`, `level?` | Mensagem com interpolação `#{...}` |

**Valores de `<param value="...">`:** começando com `#` → expressão SpEL (`#book['price']`); contendo `#{...}` → template; caso contrário → literal (número, booleano ou texto). A função `#toNumber(x)` converte textos em `BigDecimal`.

> Em XML, `<` não é permitido dentro de atributos: escreva `price &lt; :max` (ou use o operador SpEL `lt`).

## Segurança

1. **XXE** — `disallow-doctype-decl`, entidades externas gerais/de parâmetro desabilitadas, `load-external-dtd=false`, `ACCESS_EXTERNAL_DTD`/`ACCESS_EXTERNAL_SCHEMA` vazios, `FEATURE_SECURE_PROCESSING`, XInclude off e `EntityResolver` que rejeita tudo. O mesmo parser é usado para workflows, `read-xml` e o XHTML do PDF.
2. **Sandbox SpEL** — `TypeLocator` que lança exceção para qualquer `T(...)`; construtores (`new ...`) bloqueados; apenas métodos de instância via `SecureMethodResolver` (bloqueia `getClass`, reflexão, `ClassLoader`, `Runtime`, `ProcessBuilder`, I/O, rede); propriedades somente leitura.
3. **SQL Injection** — exclusivamente `NamedParameterJdbcTemplate` com parâmetros vinculados; validação prévia rejeita `;`, comentários (`--`, `/*`) e comandos fora da lista permitida por tag (sem DDL).
4. **Sistema de arquivos** — `read-xml` e `generate-pdf` só acessam `rules.input-dir` / `rules.output-dir` (sem caminhos absolutos ou `../`); PDF deve ter extensão `.pdf`.
5. **Templates/PDF** — partials Mustache bloqueados; Flying Saucer não carrega recursos externos (sem SSRF).
6. **Limites** — `max-steps`, `max-depth`, `max-iterations` e `max-workflow-size` configuráveis em `application.yml`.

Erros retornam `application/problem+json`: **400** para violações de segurança, **422** para erros de execução.

## Testes

```bash
mvn test
```

Cobrem o workflow de exemplo (ofertas + PDF), `if/else`, escopo do `foreach`, `read-xml` com XPath, rollback transacional, a API REST e os bloqueios de segurança (XXE, `T(...)`, reflexão, SQL perigoso, path traversal, partials).
