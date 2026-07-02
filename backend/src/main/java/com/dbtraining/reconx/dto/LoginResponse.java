package com.dbtraining.reconx.dto;

/** JWT envelope returned to clients. */
public record LoginResponse(String token, String tokenType, long expiresInSeconds, String role) {}
