package com.inno.impl.dto.register;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank(message = "Name should not be blank")
        @Size(max = 50, message = "Name length should not exceed 50 characters")
        String name,

        @NotBlank(message = "Surname should not be blank")
        @Size(max = 50, message = "Surname length should not exceed 50 characters")
        String surname,

        @Past(message = "Birth date should be in the past")
        LocalDate birthDate,

        @NotBlank(message = "Email should not be blank")
        @Email(message = "Email should be valid")
        String email,

        @NotBlank(message = "Password should not be blank")
        @Size(min = 6, max = 36, message = "Password length should be between 6 and 36")
        String password
) { }