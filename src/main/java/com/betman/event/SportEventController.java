package com.betman.event;

import com.betman.common.web.PageResponse;
import com.betman.common.web.Paging;
import com.betman.event.dto.SportEventResponse;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class SportEventController {

	private final SportEventService eventService;

	public SportEventController(SportEventService eventService) {
		this.eventService = eventService;
	}

	@GetMapping
	public PageResponse<SportEventResponse> list(@RequestParam(required = false) EventStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = Paging.DEFAULT_SIZE) int size) {
		// finished events are more useful newest-first; open events soonest-first
		Sort sort = status == EventStatus.FINISHED
				? Sort.by("finishedAt", "id").descending()
				: Sort.by("startsAt", "id").ascending();
		return eventService.list(status, Paging.of(page, size, sort));
	}

	@GetMapping("/{id}")
	public SportEventResponse get(@PathVariable Long id) {
		return eventService.get(id);
	}
}
