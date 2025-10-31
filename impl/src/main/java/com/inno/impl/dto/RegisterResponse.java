package com.inno.impl.dto;

public record RegisterResponse(
        Long userId,
        String email,
        String message
) { }