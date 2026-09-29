package com.betman.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betman.event.EventStatus;
import com.betman.event.SportEvent;
import com.betman.event.SportEventService;
import com.betman.odds.Odds;
import com.betman.odds.OddsCalculator;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventGeneratorTest {

	private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

	@Mock
	private SportEventService eventService;

	@Mock
	private OddsCalculator oddsCalculator;

	private EventGenerator generator;

	@BeforeEach
	void setUp() {
		generator = new EventGenerator(eventService, oddsCalculator, new Random(11L), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void generatesRequestedNumberOfEventsWithDistinctTeamsAndFutureKickOff() {
		Odds odds = new Odds(new BigDecimal("2.10"), new BigDecimal("3.49"), new BigDecimal("3.37"));
		when(oddsCalculator.calculate()).thenReturn(odds);
		when(eventService.create(any(), any(), any(), any())).thenAnswer(inv -> SportEvent.builder()
				.homeTeam(inv.getArgument(0)).awayTeam(inv.getArgument(1)).startsAt(inv.getArgument(2))
				.status(EventStatus.SCHEDULED).build());

		List<SportEvent> created = generator.generate(25);

		assertThat(created).hasSize(25);
		ArgumentCaptor<String> home = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> away = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Instant> startsAt = ArgumentCaptor.forClass(Instant.class);
		verify(eventService, times(25)).create(home.capture(), away.capture(), startsAt.capture(), any());
		for (int i = 0; i < 25; i++) {
			assertThat(home.getAllValues().get(i)).isIn(TeamNames.ALL);
			assertThat(away.getAllValues().get(i)).isIn(TeamNames.ALL).isNotEqualTo(home.getAllValues().get(i));
			assertThat(startsAt.getAllValues().get(i)).isBetween(NOW.plusSeconds(60), NOW.plusSeconds(600));
		}
	}

	@Test
	void generatesNothingForZeroOrNegativeCount() {
		assertThat(generator.generate(0)).isEmpty();
		assertThat(generator.generate(-3)).isEmpty();
		verify(eventService, never()).create(any(), any(), any(), any());
		verify(oddsCalculator, never()).calculate();
	}

	@Test
	void teamListHasSixteenDistinctNames() {
		assertThat(TeamNames.ALL).hasSize(16).doesNotHaveDuplicates();
	}
}
