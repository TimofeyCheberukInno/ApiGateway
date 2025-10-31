package com.inno.impl.dto.register;

public record RegisterResponse(
        Long userId,
        String email,
        String message
) { }