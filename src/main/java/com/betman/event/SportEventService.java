package com.betman.event;

import com.betman.common.error.EventAlreadySettledException;
import com.betman.common.error.FieldErrorDto;
import com.betman.common.error.NotFoundException;
import com.betman.common.error.RequestValidationException;
import com.betman.common.web.PageResponse;
import com.betman.event.dto.CreateEventRequest;
import com.betman.event.dto.SportEventResponse;
import com.betman.odds.LiveOdds;
import com.betman.odds.LiveOddsProvider;
import com.betman.odds.Odds;
import com.betman.odds.OddsCalculator;
import com.betman.settlement.SettlementService;
import com.betman.settlement.SettlementSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class SportEventService {

	private final SportEventRepository eventRepository;
	private final OddsCalculator oddsCalculator;
	private final LiveOddsProvider liveOddsProvider;
	private final SettlementService settlementService;
	private final Clock clock;

	public SportEventService(SportEventRepository eventRepository, OddsCalculator oddsCalculator,
			LiveOddsProvider liveOddsProvider, SettlementService settlementService, Clock clock) {
		this.eventRepository = eventRepository;
		this.oddsCalculator = oddsCalculator;
		this.liveOddsProvider = liveOddsProvider;
		this.settlementService = settlementService;
		this.clock = clock;
	}

	// ---- queries -------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public PageResponse<SportEventResponse> list(EventStatus status, Pageable pageable) {
		var page = status == null ? eventRepository.findAll(pageable) : eventRepository.findByStatus(status, pageable);
		return PageResponse.from(page, SportEventResponse::from);
	}

	@Transactional(readOnly = true)
	public SportEventResponse get(Long id) {
		return SportEventResponse.from(getOrThrow(id));
	}

	@Transactional(readOnly = true)
	public SportEvent getOrThrow(Long id) {
		return eventRepository.findById(id).orElseThrow(() -> NotFoundException.event(id));
	}

	/** Odds from the simulated external provider; may throw OddsProviderUnavailableException. */
	@Transactional(readOnly = true)
	public LiveOdds getLiveOdds(Long id) {
		return liveOddsProvider.fetch(getOrThrow(id));
	}

	@Transactional(readOnly = true)
	public List<SportEvent> findAllById(List<Long> ids) {
		return ids.isEmpty() ? List.of() : eventRepository.findAllById(ids);
	}

	@Transactional(readOnly = true)
	public List<SportEvent> findScheduledStartingBefore(Instant threshold) {
		return eventRepository.findByStatusAndStartsAtLessThanEqual(EventStatus.SCHEDULED, threshold);
	}

	@Transactional(readOnly = true)
	public List<SportEvent> findLiveStartedBefore(Instant threshold) {
		return eventRepository.findByStatusAndStartsAtLessThanEqual(EventStatus.LIVE, threshold);
	}

	@Transactional(readOnly = true)
	public long countByStatus(EventStatus status) {
		return eventRepository.countByStatus(status);
	}

	// ---- commands ------------------------------------------------------------------------

	/** Manual creation through the admin API. Missing odds are generated. */
	@Transactional
	public SportEventResponse create(CreateEventRequest request) {
		String home = request.homeTeam().trim();
		String away = request.awayTeam().trim();
		if (home.equalsIgnoreCase(away)) {
			throw new RequestValidationException("Os times devem ser diferentes.",
					List.of(new FieldErrorDto("awayTeam", "o time visitante deve ser diferente do time da casa")));
		}
		Odds odds = request.hasAllOdds()
				? new Odds(request.oddHome(), request.oddDraw(), request.oddAway())
				: oddsCalculator.calculate();
		return SportEventResponse.from(create(home, away, request.startsAt(), odds));
	}

	@Transactional
	public SportEvent create(String homeTeam, String awayTeam, Instant startsAt, Odds odds) {
		SportEvent event = eventRepository.save(SportEvent.builder()
				.sport(Sport.FOOTBALL)
				.homeTeam(homeTeam)
				.awayTeam(awayTeam)
				.startsAt(startsAt)
				.status(EventStatus.SCHEDULED)
				.oddHome(odds.home())
				.oddDraw(odds.draw())
				.oddAway(odds.away())
				.createdAt(clock.instant())
				.build());
		log.info("Event created eventId={} homeTeam={} awayTeam={} startsAt={} oddHome={} oddDraw={} oddAway={}",
				event.getId(), homeTeam, awayTeam, startsAt, odds.home(), odds.draw(), odds.away());
		return event;
	}

	/** SCHEDULED -> LIVE. */
	@Transactional
	public SportEventResponse start(Long id) {
		SportEvent event = getOrThrow(id);
		if (event.getStatus() != EventStatus.SCHEDULED) {
			log.debug("Event start skipped eventId={} status={}", id, event.getStatus());
			return SportEventResponse.from(event);
		}
		event.setStatus(EventStatus.LIVE);
		eventRepository.save(event);
		log.info("Event started eventId={} homeTeam={} awayTeam={} startsAt={}", id, event.getHomeTeam(),
				event.getAwayTeam(), event.getStartsAt());
		return SportEventResponse.from(event);
	}

	/**
	 * SCHEDULED/LIVE -> FINISHED with the given result, then settles every pending bet in the same
	 * transaction. Finishing an already finished event is rejected with EVENT_ALREADY_SETTLED.
	 */
	@Transactional
	public SettlementSummary finish(Long id, Outcome result) {
		SportEvent event = getOrThrow(id);
		if (event.getStatus() == EventStatus.FINISHED) {
			throw new EventAlreadySettledException(id);
		}
		event.setStatus(EventStatus.FINISHED);
		event.setResult(result);
		event.setFinishedAt(clock.instant());
		eventRepository.save(event);
		log.info("Event finished eventId={} homeTeam={} awayTeam={} result={}", id, event.getHomeTeam(),
				event.getAwayTeam(), result);
		return settlementService.settleEvent(event);
	}
}
