package com.example.agenticrag.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.example.agenticrag.exception.RepoNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AgenticRagService {

    private static final Logger log = LoggerFactory.getLogger(AgenticRagService.class);

    private static final int MAX_ITERATIONS = 6;
    private static final Pattern FINAL_PATTERN = Pattern.compile("FINAL:\\s*([\\s\\S]+)");
    private static final Pattern INPUT_PATTERN = Pattern.compile("INPUT:\\s*(.+)");

    private static final String SYSTEM_PROMPT = """
            Você é um engenheiro de software analisando um repositório GitHub.

            REGRAS:
            1. SEMPRE comece lendo o README.md.
            2. NUNCA responda sem ler pelo menos um arquivo.
            3. Leia arquivos adicionais conforme necessário para responder com precisão.

            TOOL disponível: read_file

            Para ler um arquivo, responda EXATAMENTE assim (sem texto extra antes):
            TOOL: read_file
            INPUT: caminho/do/arquivo

            Quando tiver a resposta completa, responda EXATAMENTE assim:
            FINAL: sua resposta aqui
            """;

    private final AnthropicClient anthropic;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String githubToken;
    private final String githubApiVersion;
    private final String claudeModel;
    private final long maxTokens;

    public AgenticRagService(
            AnthropicClient anthropic,
            HttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${github.token}") String githubToken,
            @Value("${github.api-version}") String githubApiVersion,
            @Value("${anthropic.model}") String claudeModel,
            @Value("${anthropic.max-tokens}") long maxTokens) {
        this.anthropic = anthropic;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.githubToken = githubToken;
        this.githubApiVersion = githubApiVersion;
        this.claudeModel = claudeModel;
        this.maxTokens = maxTokens;
    }

    public String ask(String question, String repoInput) {
        String repo = normalizeRepo(repoInput);
        log.info("Iniciando consulta: repo={}", repo);
        validateRepo(repo);

        List<MessageParam> history = new ArrayList<>();
        history.add(userMessage("Pergunta: " + question));

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            log.debug("Iteração {}/{}", i + 1, MAX_ITERATIONS);
            String text = callClaude(history);
            history.add(assistantMessage(text));

            Matcher finalMatcher = FINAL_PATTERN.matcher(text);
            if (finalMatcher.find()) {
                log.info("Resposta final obtida na iteração {}", i + 1);
                return finalMatcher.group(1).trim();
            }

            Matcher inputMatcher = INPUT_PATTERN.matcher(text);
            if (!inputMatcher.find()) {
                log.warn("Iteração {}: Claude não solicitou arquivo nem forneceu FINAL", i + 1);
                return "Claude não solicitou arquivo nem forneceu resposta final. Última resposta:\n" + text;
            }

            String filePath = inputMatcher.group(1).trim();
            log.debug("Buscando arquivo do GitHub: {}", filePath);
            history.add(userMessage(buildFileResultContent(filePath, fetchGithubFile(repo, filePath))));
        }

        log.warn("Limite de {} iterações atingido para repo={}", MAX_ITERATIONS, repo);
        return "Limite de iterações atingido sem resposta final.";
    }

    private String callClaude(List<MessageParam> history) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(claudeModel)
                .maxTokens(maxTokens)
                .system(SYSTEM_PROMPT);

        for (MessageParam msg : history) {
            builder.addMessage(msg);
        }

        Message response = anthropic.messages().create(builder.build());

        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .collect(Collectors.joining());
    }

    private String fetchGithubFile(String repo, String path) {
        try {
            String url = "https://api.github.com/repos/" + repo + "/contents/" + path;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + githubToken)
                    .header("X-GitHub-Api-Version", githubApiVersion)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("GitHub retornou HTTP {} ao buscar arquivo '{}'", response.statusCode(), path);
                return "Erro ao buscar arquivo (HTTP %d): %s".formatted(response.statusCode(), path);
            }

            JsonNode node = objectMapper.readTree(response.body());
            String base64Content = node.get("content").asText().replaceAll("\\s", "");
            return new String(Base64.getDecoder().decode(base64Content), StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Falha ao buscar arquivo '{}': {}", path, e.getMessage(), e);
            return "Erro ao ler arquivo '%s': %s".formatted(path, e.getMessage());
        }
    }

    private void validateRepo(String repo) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + repo))
                    .header("Accept", "application/vnd.github+json")
                    .header("Authorization", "Bearer " + githubToken)
                    .header("X-GitHub-Api-Version", githubApiVersion)
                    .GET()
                    .build();

            int status = httpClient.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 404) throw new RepoNotFoundException(repo);
            if (status != 200) throw new RuntimeException("GitHub retornou HTTP " + status + " ao validar o repositório.");
        } catch (RepoNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Erro ao validar repositório '{}': {}", repo, e.getMessage(), e);
            throw new RuntimeException("Erro ao validar repositório: " + e.getMessage(), e);
        }
    }

    private String normalizeRepo(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Campo 'repo' é obrigatório.");
        }
        return input.replaceAll("https?://github\\.com/", "").replaceAll("/$", "");
    }

    private MessageParam userMessage(String content) {
        return MessageParam.builder().role(MessageParam.Role.USER).content(content).build();
    }

    private MessageParam assistantMessage(String content) {
        return MessageParam.builder().role(MessageParam.Role.ASSISTANT).content(content).build();
    }

    private String buildFileResultContent(String filePath, String fileContent) {
        return """
                Conteúdo de %s:

                %s

                Continue analisando. Leia mais arquivos se necessário (TOOL: read_file / INPUT: ...) \
                ou forneça a resposta final (FINAL: ...).
                """.formatted(filePath, fileContent);
    }
}
