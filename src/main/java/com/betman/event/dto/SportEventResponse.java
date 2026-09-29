package com.betman.event.dto;

import com.betman.event.EventStatus;
import com.betman.event.Outcome;
import com.betman.event.Sport;
import com.betman.event.SportEvent;
import java.math.BigDecimal;
import java.time.Instant;

public record SportEventResponse(
		Long id,
		Sport sport,
		String homeTeam,
		String awayTeam,
		Instant startsAt,
		EventStatus status,
		BigDecimal oddHome,
		BigDecimal oddDraw,
		BigDecimal oddAway,
		Outcome result,
		Instant finishedAt,
		Instant createdAt) {

	public static SportEventResponse from(SportEvent event) {
		return new SportEventResponse(event.getId(), event.getSport(), event.getHomeTeam(), event.getAwayTeam(),
				event.getStartsAt(), event.getStatus(), event.getOddHome(), event.getOddDraw(), event.getOddAway(),
				event.getResult(), event.getFinishedAt(), event.getCreatedAt());
	}
}
