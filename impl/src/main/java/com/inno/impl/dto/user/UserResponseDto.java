package com.inno.impl.dto.user;

import java.time.LocalDate;

public record UserResponseDto(
        Long id,
        String name,
        String surname,
        LocalDate birthDate,
        String email
) { }