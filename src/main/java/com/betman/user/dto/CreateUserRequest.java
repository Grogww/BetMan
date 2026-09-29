package com.betman.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateUserRequest(
		@NotBlank(message = "informe o nome de usuário")
		@Pattern(regexp = "^[a-zA-Z0-9_]{3,30}$",
				message = "use de 3 a 30 caracteres: letras, números ou _")
		String username) {
}
