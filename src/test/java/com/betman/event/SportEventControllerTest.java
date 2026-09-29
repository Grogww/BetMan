package com.betman.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betman.common.web.PageResponse;
import com.betman.event.dto.SportEventResponse;
import com.betman.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SportEventController.class)
class SportEventControllerTest {

	private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SportEventService eventService;

	@MockitoBean
	private UserService userService;

	@Test
	void listsEventsFilteredByStatusWithPagination() throws Exception {
		SportEventResponse event = new SportEventResponse(1L, Sport.FOOTBALL, "Tubarões do Vale",
				"Leões da Serra", NOW.plusSeconds(120), EventStatus.SCHEDULED, new BigDecimal("2.10"),
				new BigDecimal("3.49"), new BigDecimal("3.37"), null, null, NOW);
		when(eventService.list(eq(EventStatus.SCHEDULED), any()))
				.thenReturn(new PageResponse<>(List.of(event), 0, 100, 1, 1));

		mockMvc.perform(get("/api/events").param("status", "SCHEDULED").param("size", "500"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].homeTeam").value("Tubarões do Vale"))
				.andExpect(jsonPath("$.content[0].status").value("SCHEDULED"))
				.andExpect(jsonPath("$.totalElements").value(1));

		// page size is capped at 100
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(eventService).list(eq(EventStatus.SCHEDULED), pageable.capture());
		org.assertj.core.api.Assertions.assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
	}

	@Test
	void listsAllEventsWhenNoStatusGiven() throws Exception {
		when(eventService.list(isNull(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		mockMvc.perform(get("/api/events"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void invalidStatusFilterIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/events").param("status", "BOGUS"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}
}
