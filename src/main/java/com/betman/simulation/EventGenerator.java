package com.betman.simulation;

import com.betman.event.SportEvent;
import com.betman.event.SportEventService;
import com.betman.odds.OddsCalculator;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Creates new SCHEDULED events with two distinct fictional teams, a kick-off between +1 and +10
 * minutes and odds from the {@link OddsCalculator}.
 */
@Slf4j
@Component
public class EventGenerator {

	static final long MIN_DELAY_SECONDS = 60;
	static final long MAX_DELAY_SECONDS = 600;

	private final SportEventService eventService;
	private final OddsCalculator oddsCalculator;
	private final RandomGenerator random;
	private final Clock clock;

	public EventGenerator(SportEventService eventService, OddsCalculator oddsCalculator, RandomGenerator random,
			Clock clock) {
		this.eventService = eventService;
		this.oddsCalculator = oddsCalculator;
		this.random = random;
		this.clock = clock;
	}

	public List<SportEvent> generate(int count) {
		List<SportEvent> created = new ArrayList<>(Math.max(count, 0));
		for (int i = 0; i < count; i++) {
			String home = TeamNames.ALL.get(random.nextInt(TeamNames.ALL.size()));
			String away;
			do {
				away = TeamNames.ALL.get(random.nextInt(TeamNames.ALL.size()));
			} while (away.equals(home));
			Instant startsAt = clock.instant()
					.plusSeconds(MIN_DELAY_SECONDS + random.nextLong(MAX_DELAY_SECONDS - MIN_DELAY_SECONDS + 1));
			created.add(eventService.create(home, away, startsAt, oddsCalculator.calculate()));
		}
		if (!created.isEmpty()) {
			log.info("Events generated count={}", created.size());
		}
		return created;
	}
}
