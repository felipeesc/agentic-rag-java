package com.example.agenticrag.dto;

import jakarta.validation.constraints.NotBlank;

public record QuestionRequest(
        @NotBlank(message = "Campo 'question' é obrigatório.")
        String question,

        @NotBlank(message = "Campo 'repo' é obrigatório.")
        String repo
) {}
