package com.betman.admin;

import com.betman.admin.dto.SettleEventRequest;
import com.betman.admin.dto.SettleEventResponse;
import com.betman.event.SportEventService;
import com.betman.event.dto.CreateEventRequest;
import com.betman.event.dto.SportEventResponse;
import com.betman.settlement.SettlementSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Manual control of the simulation: create events and force results. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final SportEventService eventService;

	public AdminController(SportEventService eventService) {
		this.eventService = eventService;
	}

	@PostMapping("/events")
	@ResponseStatus(HttpStatus.CREATED)
	public SportEventResponse createEvent(@Valid @RequestBody CreateEventRequest request) {
		return eventService.create(request);
	}

	@PostMapping("/events/{id}/settle")
	public SettleEventResponse settleEvent(@PathVariable Long id, @Valid @RequestBody SettleEventRequest request) {
		SettlementSummary summary = eventService.finish(id, request.result());
		return new SettleEventResponse(eventService.get(id), summary.won(), summary.lost(), summary.paid());
	}
}
