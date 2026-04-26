package com.example.agenticrag.exception;

public class RepoNotFoundException extends RuntimeException {
    public RepoNotFoundException(String repo) {
        super("Repositório não encontrado: " + repo);
    }
}
