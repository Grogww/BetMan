package com.betman.admin.dto;

import com.betman.event.Outcome;
import jakarta.validation.constraints.NotNull;

public record SettleEventRequest(@NotNull(message = "informe o resultado (HOME, DRAW ou AWAY)") Outcome result) {
}
