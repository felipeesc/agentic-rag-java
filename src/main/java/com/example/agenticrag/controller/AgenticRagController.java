package com.example.agenticrag.controller;

import com.example.agenticrag.dto.AnswerResponse;
import com.example.agenticrag.dto.ErrorResponse;
import com.example.agenticrag.dto.QuestionRequest;
import com.example.agenticrag.exception.RepoNotFoundException;
import com.example.agenticrag.service.AgenticRagService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/ask")
public class AgenticRagController {

    private final AgenticRagService service;

    public AgenticRagController(AgenticRagService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AnswerResponse> ask(@Valid @RequestBody QuestionRequest request) {
        return ResponseEntity.ok(new AnswerResponse(service.ask(request.question(), request.repo())));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ErrorResponse(message));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(RepoNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleRepoNotFound(RepoNotFoundException ex) {
        return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError().body(new ErrorResponse("Erro interno ao processar a solicitação."));
    }
}
