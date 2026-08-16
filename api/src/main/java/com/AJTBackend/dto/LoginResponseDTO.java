package com.AJTBackend.dto;

public record LoginResponseDTO(
        String token,
        String username,
        String role
) {}