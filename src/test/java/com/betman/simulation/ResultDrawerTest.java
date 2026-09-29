package com.betman.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.betman.event.Outcome;
import com.betman.event.SportEvent;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ResultDrawerTest {

	// strong favourite at home: implied probabilities ~ 64% / 21% / 15%
	private final SportEvent event = SportEvent.builder().id(1L).oddHome(new BigDecimal("1.50"))
			.oddDraw(new BigDecimal("4.50")).oddAway(new BigDecimal("6.50")).build();

	@Test
	void sameSeedGivesSameSequenceOfResults() {
		ResultDrawer first = new ResultDrawer(new Random(42L));
		ResultDrawer second = new ResultDrawer(new Random(42L));

		for (int i = 0; i < 50; i++) {
			assertThat(first.draw(event)).isEqualTo(second.draw(event));
		}
	}

	@Test
	void favouriteWinsMoreOftenOverManyDraws() {
		ResultDrawer drawer = new ResultDrawer(new Random(2026L));
		Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);
		int draws = 5000;

		for (int i = 0; i < draws; i++) {
			counts.merge(drawer.draw(event), 1, Integer::sum);
		}

		int home = counts.getOrDefault(Outcome.HOME, 0);
		int draw = counts.getOrDefault(Outcome.DRAW, 0);
		int away = counts.getOrDefault(Outcome.AWAY, 0);
		assertThat(home).isGreaterThan(draw).isGreaterThan(away);
		assertThat(draw).isGreaterThan(away);
		// ~64% expected for HOME; allow a generous statistical margin
		assertThat((double) home / draws).isBetween(0.58, 0.70);
	}

	@Test
	void everyOutcomeIsPossible() {
		ResultDrawer drawer = new ResultDrawer(new Random(7L));
		Map<Outcome, Integer> counts = new EnumMap<>(Outcome.class);

		for (int i = 0; i < 1000; i++) {
			counts.merge(drawer.draw(event), 1, Integer::sum);
		}

		assertThat(counts).containsKeys(Outcome.HOME, Outcome.DRAW, Outcome.AWAY);
	}
}
