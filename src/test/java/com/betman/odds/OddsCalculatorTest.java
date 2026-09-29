package com.betman.odds;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Random;
import org.junit.jupiter.api.Test;

class OddsCalculatorTest {

	@Test
	void oddsAreNeverBelowMinimum() {
		OddsCalculator calculator = new OddsCalculator(new Random(42L));

		for (int i = 0; i < 500; i++) {
			Odds odds = calculator.calculate();
			assertThat(odds.home()).isGreaterThanOrEqualTo(OddsCalculator.MIN_ODD);
			assertThat(odds.draw()).isGreaterThanOrEqualTo(OddsCalculator.MIN_ODD);
			assertThat(odds.away()).isGreaterThanOrEqualTo(OddsCalculator.MIN_ODD);
			assertThat(odds.home().scale()).isEqualTo(2);
			assertThat(odds.draw().scale()).isEqualTo(2);
			assertThat(odds.away().scale()).isEqualTo(2);
		}
	}

	@Test
	void houseMarginMakesImpliedProbabilitiesExceedOneHundredPercent() {
		OddsCalculator calculator = new OddsCalculator(new Random(7L));

		for (int i = 0; i < 500; i++) {
			Odds odds = calculator.calculate();
			BigDecimal overround = inverse(odds.home()).add(inverse(odds.draw())).add(inverse(odds.away()));
			// 6% margin, with some slack for the 2-decimal rounding of each odd
			assertThat(overround).isGreaterThan(BigDecimal.ONE);
			assertThat(overround).isBetween(new BigDecimal("1.03"), new BigDecimal("1.09"));
		}
	}

	@Test
	void drawProbabilityStaysBetweenTwentyAndThirtyTwoPercent() {
		OddsCalculator calculator = new OddsCalculator(new Random(99L));

		for (int i = 0; i < 500; i++) {
			Odds odds = calculator.calculate();
			// implied draw probability without the margin: 1 / (odd * 1.06)
			double drawProbability = 1.0 / (odds.draw().doubleValue() * OddsCalculator.HOUSE_MARGIN);
			assertThat(drawProbability).isBetween(0.19, 0.33);
		}
	}

	@Test
	void extremelyLikelyOutcomeIsClampedToMinimumOdd() {
		assertThat(OddsCalculator.toOdd(0.999)).isEqualByComparingTo("1.01");
	}

	@Test
	void sameSeedProducesSameOdds() {
		Odds first = new OddsCalculator(new Random(2026L)).calculate();
		Odds second = new OddsCalculator(new Random(2026L)).calculate();

		assertThat(first).isEqualTo(second);
	}

	private static BigDecimal inverse(BigDecimal odd) {
		return BigDecimal.ONE.divide(odd, MathContext.DECIMAL64);
	}
}
