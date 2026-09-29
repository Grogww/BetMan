package com.betman.bet.dto;

import com.betman.bet.Bet;
import com.betman.bet.BetStatus;
import com.betman.event.Outcome;
import com.betman.event.SportEvent;
import java.math.BigDecimal;
import java.time.Instant;

public record BetResponse(
		Long id,
		Long eventId,
		String homeTeam,
		String awayTeam,
		Outcome selection,
		BigDecimal stake,
		BigDecimal odd,
		BigDecimal potentialPayout,
		BetStatus status,
		Instant placedAt,
		Instant settledAt,
		/** Balance right after placing the bet; null on listings. */
		BigDecimal walletBalance) {

	public static BetResponse from(Bet bet, SportEvent event, BigDecimal walletBalance) {
		return new BetResponse(bet.getId(), bet.getEventId(), event != null ? event.getHomeTeam() : null,
				event != null ? event.getAwayTeam() : null, bet.getSelection(), bet.getStake(), bet.getOdd(),
				bet.getPotentialPayout(), bet.getStatus(), bet.getPlacedAt(), bet.getSettledAt(), walletBalance);
	}
}
