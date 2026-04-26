package com.example.agenticrag.controller;

import com.example.agenticrag.dto.QuestionRequest;
import com.example.agenticrag.exception.RepoNotFoundException;
import com.example.agenticrag.service.AgenticRagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AgenticRagController.class)
class AgenticRagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgenticRagService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void ask_returns200_whenRequestIsValid() throws Exception {
        when(service.ask("O que faz este projeto?", "owner/repo")).thenReturn("É um projeto de RAG.");

        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionRequest("O que faz este projeto?", "owner/repo"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("É um projeto de RAG."));
    }

    @Test
    void ask_returns400_whenQuestionIsBlank() throws Exception {
        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionRequest("", "owner/repo"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Campo 'question' é obrigatório."));
    }

    @Test
    void ask_returns400_whenRepoIsBlank() throws Exception {
        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionRequest("Pergunta?", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Campo 'repo' é obrigatório."));
    }

    @Test
    void ask_returns404_whenRepoNotFound() throws Exception {
        when(service.ask(any(), any())).thenThrow(new RepoNotFoundException("owner/repo"));

        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionRequest("Pergunta?", "owner/repo"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void ask_returns500_whenUnexpectedErrorOccurs() throws Exception {
        when(service.ask(any(), any())).thenThrow(new RuntimeException("erro inesperado"));

        mockMvc.perform(post("/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new QuestionRequest("Pergunta?", "owner/repo"))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Erro interno ao processar a solicitação."));
    }
}
