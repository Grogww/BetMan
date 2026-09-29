package com.betman.wallet.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record AmountRequest(
		@NotNull(message = "informe o valor")
		@Positive(message = "o valor deve ser maior que zero")
		@Digits(integer = 10, fraction = 2, message = "use no máximo 2 casas decimais")
		BigDecimal amount) {
}
