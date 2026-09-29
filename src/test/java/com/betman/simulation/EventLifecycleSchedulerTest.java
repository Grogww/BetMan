package com.betman.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betman.config.SimulationProperties;
import com.betman.event.EventStatus;
import com.betman.event.Outcome;
import com.betman.event.SportEvent;
import com.betman.event.SportEventService;
import com.betman.settlement.SettlementSummary;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventLifecycleSchedulerTest {

	private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
	private static final int MATCH_DURATION = 120;
	private static final int MIN_SCHEDULED = 8;

	@Mock
	private SportEventService eventService;

	@Mock
	private ResultDrawer resultDrawer;

	@Mock
	private EventGenerator eventGenerator;

	private EventLifecycleScheduler scheduler;

	@BeforeEach
	void setUp() {
		SimulationProperties properties = new SimulationProperties(true, MIN_SCHEDULED, MATCH_DURATION, 15000);
		scheduler = new EventLifecycleScheduler(eventService, resultDrawer, eventGenerator, properties,
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void scheduledEventsWhoseKickOffHasPassedAreStarted() {
		SportEvent due = event(1L, EventStatus.SCHEDULED, NOW.minusSeconds(5));
		SportEvent alsoDue = event(2L, EventStatus.SCHEDULED, NOW);
		when(eventService.findScheduledStartingBefore(NOW)).thenReturn(List.of(due, alsoDue));
		when(eventService.countByStatus(EventStatus.SCHEDULED)).thenReturn((long) MIN_SCHEDULED);

		scheduler.tick();

		verify(eventService).start(1L);
		verify(eventService).start(2L);
		verify(eventService, never()).finish(anyLong(), any());
	}

	@Test
	void liveEventsAreFinishedWithDrawnResultAfterMatchDuration() {
		SportEvent live = event(3L, EventStatus.LIVE, NOW.minusSeconds(MATCH_DURATION));
		when(eventService.findLiveStartedBefore(NOW.minusSeconds(MATCH_DURATION))).thenReturn(List.of(live));
		when(resultDrawer.draw(live)).thenReturn(Outcome.AWAY);
		when(eventService.finish(3L, Outcome.AWAY)).thenReturn(new SettlementSummary(1, 2, new BigDecimal("31.00")));
		when(eventService.countByStatus(EventStatus.SCHEDULED)).thenReturn((long) MIN_SCHEDULED);

		scheduler.tick();

		verify(eventService).finish(3L, Outcome.AWAY);
		verify(eventService, never()).start(anyLong());
	}

	@Test
	void generatesMissingEventsWhenBelowMinimum() {
		when(eventService.countByStatus(EventStatus.SCHEDULED)).thenReturn(5L);
		when(eventGenerator.generate(3)).thenReturn(List.of(event(10L, EventStatus.SCHEDULED, NOW.plusSeconds(60)),
				event(11L, EventStatus.SCHEDULED, NOW.plusSeconds(120)),
				event(12L, EventStatus.SCHEDULED, NOW.plusSeconds(180))));

		scheduler.tick();

		verify(eventGenerator).generate(3);
	}

	@Test
	void doesNotGenerateWhenMinimumIsAlreadyMet() {
		when(eventService.countByStatus(EventStatus.SCHEDULED)).thenReturn(9L);

		scheduler.tick();

		verify(eventGenerator, never()).generate(anyInt());
	}

	@Test
	void failureOnOneEventDoesNotStopTheOthers() {
		SportEvent broken = event(1L, EventStatus.SCHEDULED, NOW.minusSeconds(5));
		SportEvent fine = event(2L, EventStatus.SCHEDULED, NOW.minusSeconds(5));
		when(eventService.findScheduledStartingBefore(NOW)).thenReturn(List.of(broken, fine));
		when(eventService.start(1L)).thenThrow(new IllegalStateException("boom"));

		int started = scheduler.startDueEvents(NOW);

		assertThat(started).isEqualTo(1);
		verify(eventService).start(2L);
	}

	private static SportEvent event(Long id, EventStatus status, Instant startsAt) {
		return SportEvent.builder().id(id).homeTeam("Casa " + id).awayTeam("Fora " + id).status(status)
				.startsAt(startsAt).oddHome(new BigDecimal("2.00")).oddDraw(new BigDecimal("3.00"))
				.oddAway(new BigDecimal("4.00")).build();
	}
}
