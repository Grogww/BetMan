package com.betman.event;

import com.betman.odds.LiveOdds;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Exposes the simulated external odds provider: {@code GET /api/events/{id}/odds/live}. */
@RestController
public class LiveOddsController {

	private final SportEventService eventService;

	public LiveOddsController(SportEventService eventService) {
		this.eventService = eventService;
	}

	@GetMapping("/api/events/{id}/odds/live")
	public LiveOdds liveOdds(@PathVariable Long id) {
		return eventService.getLiveOdds(id);
	}
}
