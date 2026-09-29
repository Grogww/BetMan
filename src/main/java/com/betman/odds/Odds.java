package com.betman.odds;

import java.math.BigDecimal;

/** Decimal odds of the three outcomes of a match. */
public record Odds(BigDecimal home, BigDecimal draw, BigDecimal away) {
}
