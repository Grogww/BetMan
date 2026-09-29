package com.betman.stats.dto;

import com.betman.bet.BetStatus;
import com.betman.event.EventStatus;
import java.math.BigDecimal;
import java.util.Map;

public record StatsSummaryResponse(
		long users,
		Map<BetStatus, Long> betsByStatus,
		BigDecimal totalStaked,
		BigDecimal totalPaid,
		Map<EventStatus, Long> eventsByStatus) {
}
