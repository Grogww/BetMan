package com.betman.odds;

import java.math.BigDecimal;
import java.time.Instant;

/** Snapshot returned by the simulated external provider. Never persisted. */
public record LiveOdds(Long eventId, BigDecimal oddHome, BigDecimal oddDraw, BigDecimal oddAway, long latencyMs,
		Instant fetchedAt) {
}
