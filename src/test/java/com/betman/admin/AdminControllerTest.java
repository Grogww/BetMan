package com.betman.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betman.common.error.EventAlreadySettledException;
import com.betman.event.EventStatus;
import com.betman.event.Outcome;
import com.betman.event.Sport;
import com.betman.event.SportEventService;
import com.betman.event.dto.SportEventResponse;
import com.betman.settlement.SettlementSummary;
import com.betman.support.MetricsTestConfiguration;
import com.betman.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(MetricsTestConfiguration.class)
@WebMvcTest(AdminController.class)
class AdminControllerTest {

	private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SportEventService eventService;

	@MockitoBean
	private UserService userService;

	@Test
	void createEventReturnsCreated() throws Exception {
		when(eventService.create(any())).thenReturn(response(EventStatus.SCHEDULED, null));

		mockMvc.perform(post("/api/admin/events").contentType(MediaType.APPLICATION_JSON).content("""
				{"homeTeam": "Tubarões do Vale", "awayTeam": "Leões da Serra", "startsAt": "2026-09-15T12:05:00Z"}
				"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.status").value("SCHEDULED"));
	}

	@Test
	void createEventWithoutTeamsIsBadRequest() throws Exception {
		mockMvc.perform(post("/api/admin/events").contentType(MediaType.APPLICATION_JSON)
						.content("{\"startsAt\": \"2026-09-15T12:05:00Z\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	@Test
	void settleEventReturnsSummary() throws Exception {
		when(eventService.finish(1L, Outcome.HOME)).thenReturn(new SettlementSummary(2, 3, new BigDecimal("107.50")));
		when(eventService.get(1L)).thenReturn(response(EventStatus.FINISHED, Outcome.HOME));

		mockMvc.perform(post("/api/admin/events/1/settle").contentType(MediaType.APPLICATION_JSON)
						.content("{\"result\": \"HOME\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.event.status").value("FINISHED"))
				.andExpect(jsonPath("$.event.result").value("HOME"))
				.andExpect(jsonPath("$.wonBets").value(2))
				.andExpect(jsonPath("$.lostBets").value(3))
				.andExpect(jsonPath("$.totalPaid").value(107.50));
	}

	@Test
	void settlingFinishedEventIsConflict() throws Exception {
		when(eventService.finish(1L, Outcome.DRAW)).thenThrow(new EventAlreadySettledException(1L));

		mockMvc.perform(post("/api/admin/events/1/settle").contentType(MediaType.APPLICATION_JSON)
						.content("{\"result\": \"DRAW\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("EVENT_ALREADY_SETTLED"));
	}

	@Test
	void settleWithoutResultIsBadRequest() throws Exception {
		mockMvc.perform(post("/api/admin/events/1/settle").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("result"));
	}

	private static SportEventResponse response(EventStatus status, Outcome result) {
		return new SportEventResponse(1L, Sport.FOOTBALL, "Tubarões do Vale", "Leões da Serra",
				NOW.plusSeconds(300), status, new BigDecimal("2.10"), new BigDecimal("3.49"),
				new BigDecimal("3.37"), result, result == null ? null : NOW, NOW);
	}
}
