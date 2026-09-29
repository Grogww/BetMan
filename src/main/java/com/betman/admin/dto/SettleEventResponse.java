package com.betman.admin.dto;

import com.betman.event.dto.SportEventResponse;
import java.math.BigDecimal;

public record SettleEventResponse(SportEventResponse event, int wonBets, int lostBets, BigDecimal totalPaid) {
}
