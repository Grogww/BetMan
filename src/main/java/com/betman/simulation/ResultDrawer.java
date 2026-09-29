package com.betman.simulation;

import com.betman.event.Outcome;
import com.betman.event.SportEvent;
import java.util.Locale;
import java.util.random.RandomGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Draws the result of a match using the probabilities implied by its odds:
 * {@code p(outcome) = (1 / odd) / sum(1 / odd)}.
 */
@Slf4j
@Component
public class ResultDrawer {

	private final RandomGenerator random;

	public ResultDrawer(RandomGenerator random) {
		this.random = random;
	}

	public Outcome draw(SportEvent event) {
		double home = 1.0 / event.getOddHome().doubleValue();
		double draw = 1.0 / event.getOddDraw().doubleValue();
		double away = 1.0 / event.getOddAway().doubleValue();
		double total = home + draw + away;
		double pHome = home / total;
		double pDraw = draw / total;
		double pAway = away / total;

		double roll = random.nextDouble();
		Outcome outcome;
		if (roll < pHome) {
			outcome = Outcome.HOME;
		} else if (roll < pHome + pDraw) {
			outcome = Outcome.DRAW;
		} else {
			outcome = Outcome.AWAY;
		}
		log.debug("Result drawn eventId={} pHome={} pDraw={} pAway={} roll={} result={}", event.getId(), fmt(pHome),
				fmt(pDraw), fmt(pAway), fmt(roll), outcome);
		return outcome;
	}

	private static String fmt(double value) {
		return String.format(Locale.ROOT, "%.4f", value);
	}
}
