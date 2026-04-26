# Agentic RAG Java

API que responde perguntas sobre repositórios GitHub usando um agente baseado em Claude. O agente lê arquivos do repositório de forma iterativa até ter contexto suficiente para responder.

## Como funciona

1. Recebe uma pergunta e um repositório GitHub
2. Envia a pergunta ao Claude com instruções de agente
3. Claude solicita arquivos do repositório conforme necessário
4. O ciclo se repete até o Claude produzir uma resposta final (máximo 6 iterações)

## Tecnologias

- **Java 21**
- **Spring Boot 3.3**
- **Anthropic Java SDK** — cliente para a API do Claude
- **GitHub REST API** — leitura de arquivos do repositório
- **Docker** — build e execução

## Executando

```bash
docker build -t agentic-rag .

docker run -p 8080:8080 \
  -e ANTHROPIC_API_KEY=sua_chave \
  -e GITHUB_TOKEN=seu_token \
  agentic-rag
```

## Endpoint

**POST** `/ask`

```json
{
  "question": "O que faz este projeto?",
  "repo": "owner/repo-name"
}
```

Também aceita URL completa: `"repo": "https://github.com/owner/repo-name"`

**Resposta:**

```json
{
  "answer": "Este projeto é..."
}
```

| Status | Situação |
|--------|----------|
| 200 | Resposta gerada com sucesso |
| 400 | Campos obrigatórios ausentes |
| 404 | Repositório não encontrado |
| 500 | Erro interno |
