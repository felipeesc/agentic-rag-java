package com.example.agenticrag.service;

import com.anthropic.client.AnthropicClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class AgenticRagServiceTest {

    @Mock
    private AnthropicClient anthropic;

    @Mock
    private HttpClient httpClient;

    @Mock
    private ObjectMapper objectMapper;

    private AgenticRagService service;

    @BeforeEach
    void setUp() {
        service = new AgenticRagService(
                anthropic, httpClient, objectMapper,
                "fake-token", "2022-11-28", "claude-sonnet-4-6", 2000L
        );
    }

    @Test
    void ask_throwsIllegalArgumentException_whenRepoIsNull() {
        assertThrows(IllegalArgumentException.class, () -> service.ask("pergunta", null));
    }

    @Test
    void ask_throwsIllegalArgumentException_whenRepoIsBlank() {
        assertThrows(IllegalArgumentException.class, () -> service.ask("pergunta", "  "));
    }

    @Test
    void ask_throwsIllegalArgumentException_whenRepoIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> service.ask("pergunta", ""));
    }
}
