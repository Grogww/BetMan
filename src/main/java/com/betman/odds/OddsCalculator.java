package com.betman.odds;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.random.RandomGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Draws random win probabilities and converts them to decimal odds with the house margin.
 * The draw probability stays between 20% and 32%; the rest is split between home and away.
 */
@Slf4j
@Component
public class OddsCalculator {

	public static final BigDecimal MIN_ODD = new BigDecimal("1.01");
	public static final double HOUSE_MARGIN = 1.06;

	static final double DRAW_MIN = 0.20;
	static final double DRAW_MAX = 0.32;
	static final double HOME_SHARE_MIN = 0.15;
	static final double HOME_SHARE_MAX = 0.85;

	private final RandomGenerator random;

	public OddsCalculator(RandomGenerator random) {
		this.random = random;
	}

	public Odds calculate() {
		double draw = DRAW_MIN + random.nextDouble() * (DRAW_MAX - DRAW_MIN);
		double homeShare = HOME_SHARE_MIN + random.nextDouble() * (HOME_SHARE_MAX - HOME_SHARE_MIN);
		double home = (1.0 - draw) * homeShare;
		double away = (1.0 - draw) * (1.0 - homeShare);

		// normalise so the three probabilities add up to exactly 100%
		double total = home + draw + away;
		home /= total;
		draw /= total;
		away /= total;

		Odds odds = new Odds(toOdd(home), toOdd(draw), toOdd(away));
		log.debug("Odds calculated pHome={} pDraw={} pAway={} oddHome={} oddDraw={} oddAway={}",
				fmt(home), fmt(draw), fmt(away), odds.home(), odds.draw(), odds.away());
		return odds;
	}

	/** {@code odd = 1 / (probability * margin)}, rounded to 2 decimals, never below 1.01. */
	static BigDecimal toOdd(double probability) {
		BigDecimal odd = BigDecimal.valueOf(1.0 / (probability * HOUSE_MARGIN)).setScale(2, RoundingMode.HALF_EVEN);
		return odd.max(MIN_ODD);
	}

	private static String fmt(double probability) {
		return String.format(java.util.Locale.ROOT, "%.4f", probability);
	}
}
