package com.dbtraining.reconx.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** POST /api/auth/login body. */
public record LoginRequest(@Email @NotBlank String email,
                           @NotBlank String password) {}
