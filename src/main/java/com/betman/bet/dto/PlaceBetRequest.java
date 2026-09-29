package com.betman.bet.dto;

import com.betman.event.Outcome;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PlaceBetRequest(
		@NotNull(message = "informe o evento")
		Long eventId,

		@NotNull(message = "informe a seleção (HOME, DRAW ou AWAY)")
		Outcome selection,

		@NotNull(message = "informe o valor da aposta")
		@Positive(message = "o valor da aposta deve ser maior que zero")
		@Digits(integer = 10, fraction = 2, message = "use no máximo 2 casas decimais")
		BigDecimal stake) {
}
