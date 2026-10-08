package com.betman.event;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betman.common.error.NotFoundException;
import com.betman.common.error.OddsProviderUnavailableException;
import com.betman.odds.LiveOdds;
import com.betman.support.MetricsTestConfiguration;
import com.betman.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(MetricsTestConfiguration.class)
@WebMvcTest(LiveOddsController.class)
class LiveOddsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SportEventService eventService;

	@MockitoBean
	private UserService userService;

	@Test
	void returnsLiveOddsWithLatency() throws Exception {
		when(eventService.getLiveOdds(12L)).thenReturn(new LiveOdds(12L, new BigDecimal("2.20"),
				new BigDecimal("3.25"), new BigDecimal("3.05"), 321L, Instant.parse("2026-09-15T14:02:11Z")));

		mockMvc.perform(get("/api/events/12/odds/live"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.eventId").value(12))
				.andExpect(jsonPath("$.oddHome").value(2.20))
				.andExpect(jsonPath("$.oddDraw").value(3.25))
				.andExpect(jsonPath("$.oddAway").value(3.05))
				.andExpect(jsonPath("$.latencyMs").value(321));
	}

	@Test
	void providerFailureIsServiceUnavailableWithErrorCode() throws Exception {
		when(eventService.getLiveOdds(12L)).thenThrow(new OddsProviderUnavailableException(12L));

		mockMvc.perform(get("/api/events/12/odds/live"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.status").value(503))
				.andExpect(jsonPath("$.errorCode").value("ODDS_PROVIDER_UNAVAILABLE"))
				.andExpect(jsonPath("$.title").value("Provedor de odds indisponível"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void unknownEventIsNotFound() throws Exception {
		when(eventService.getLiveOdds(999L)).thenThrow(NotFoundException.event(999L));

		mockMvc.perform(get("/api/events/999/odds/live"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
	}
}
