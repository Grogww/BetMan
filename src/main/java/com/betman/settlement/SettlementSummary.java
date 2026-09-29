package com.betman.settlement;

import java.math.BigDecimal;

/** Outcome of settling the bets of one event. */
public record SettlementSummary(int won, int lost, BigDecimal paid) {

	public static SettlementSummary empty() {
		return new SettlementSummary(0, 0, BigDecimal.ZERO.setScale(2));
	}

	public int total() {
		return won + lost;
	}
}
