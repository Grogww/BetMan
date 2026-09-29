package com.betman.simulation;

import com.betman.common.web.RequestContextFilter;
import com.betman.config.SimulationProperties;
import com.betman.event.EventStatus;
import com.betman.event.Outcome;
import com.betman.event.SportEvent;
import com.betman.event.SportEventService;
import com.betman.settlement.SettlementSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the life cycle of the events on every tick:
 * <ol>
 * <li>SCHEDULED events whose kick-off has passed go LIVE;</li>
 * <li>LIVE events that have run for {@code match-duration-seconds} get a drawn result, become
 * FINISHED and have their bets settled;</li>
 * <li>new events are generated while fewer than {@code min-scheduled-events} are SCHEDULED.</li>
 * </ol>
 * Each event is processed in its own transaction (the service methods are transactional), so a
 * failure in one of them is logged and does not stop the others.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "betman.simulation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class EventLifecycleScheduler {

	private final SportEventService eventService;
	private final ResultDrawer resultDrawer;
	private final EventGenerator eventGenerator;
	private final SimulationProperties properties;
	private final Clock clock;

	public EventLifecycleScheduler(SportEventService eventService, ResultDrawer resultDrawer,
			EventGenerator eventGenerator, SimulationProperties properties, Clock clock) {
		this.eventService = eventService;
		this.resultDrawer = resultDrawer;
		this.eventGenerator = eventGenerator;
		this.properties = properties;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${betman.simulation.tick-interval-ms}")
	public void tick() {
		MDC.put(RequestContextFilter.REQUEST_ID, "sched-" + UUID.randomUUID());
		try {
			Instant now = clock.instant();
			log.debug("Scheduler tick started now={}", now);
			int started = startDueEvents(now);
			int finished = finishDueEvents(now);
			int generated = topUpScheduledEvents();
			log.debug("Scheduler tick completed started={} finished={} generated={}", started, finished, generated);
		} catch (RuntimeException ex) {
			log.error("Scheduler tick failed", ex);
		} finally {
			MDC.remove(RequestContextFilter.REQUEST_ID);
		}
	}

	int startDueEvents(Instant now) {
		List<SportEvent> due = eventService.findScheduledStartingBefore(now);
		int started = 0;
		for (SportEvent event : due) {
			try {
				eventService.start(event.getId());
				started++;
			} catch (RuntimeException ex) {
				log.error("Failed to start event eventId={}", event.getId(), ex);
			}
		}
		return started;
	}

	int finishDueEvents(Instant now) {
		Instant threshold = now.minusSeconds(properties.matchDurationSeconds());
		List<SportEvent> due = eventService.findLiveStartedBefore(threshold);
		int finished = 0;
		for (SportEvent event : due) {
			try {
				Outcome result = resultDrawer.draw(event);
				SettlementSummary summary = eventService.finish(event.getId(), result);
				log.debug("Event finished by scheduler eventId={} result={} settledBets={}", event.getId(), result,
						summary.total());
				finished++;
			} catch (RuntimeException ex) {
				log.error("Failed to finish event eventId={}", event.getId(), ex);
			}
		}
		return finished;
	}

	int topUpScheduledEvents() {
		long scheduled = eventService.countByStatus(EventStatus.SCHEDULED);
		int missing = (int) (properties.minScheduledEvents() - scheduled);
		if (missing <= 0) {
			return 0;
		}
		try {
			return eventGenerator.generate(missing).size();
		} catch (RuntimeException ex) {
			log.error("Failed to generate events missing={}", missing, ex);
			return 0;
		}
	}
}
