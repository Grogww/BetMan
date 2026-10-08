package com.betman.stats;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betman.bet.BetStatus;
import com.betman.event.EventStatus;
import com.betman.stats.dto.StatsSummaryResponse;
import com.betman.support.MetricsTestConfiguration;
import com.betman.user.UserService;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(MetricsTestConfiguration.class)
@WebMvcTest(StatsController.class)
class StatsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private StatsService statsService;

	@MockitoBean
	private UserService userService;

	@Test
	void summaryExposesTotals() throws Exception {
		when(statsService.summary()).thenReturn(new StatsSummaryResponse(3,
				Map.of(BetStatus.PENDING, 4L, BetStatus.WON, 1L, BetStatus.LOST, 2L), new BigDecimal("175.00"),
				new BigDecimal("53.75"), Map.of(EventStatus.SCHEDULED, 8L, EventStatus.LIVE, 1L, EventStatus.FINISHED, 5L)));

		mockMvc.perform(get("/api/stats/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.users").value(3))
				.andExpect(jsonPath("$.betsByStatus.PENDING").value(4))
				.andExpect(jsonPath("$.betsByStatus.WON").value(1))
				.andExpect(jsonPath("$.totalStaked").value(175.00))
				.andExpect(jsonPath("$.totalPaid").value(53.75))
				.andExpect(jsonPath("$.eventsByStatus.SCHEDULED").value(8));
	}

	@Test
	void unexpectedFailureIsInternalErrorWithoutStackTrace() throws Exception {
		when(statsService.summary()).thenThrow(new IllegalStateException("database exploded"));

		mockMvc.perform(get("/api/stats/summary"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.title").value("Erro interno"))
				.andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado. Tente novamente mais tarde."))
				.andExpect(jsonPath("$.stackTrace").doesNotExist())
				.andExpect(jsonPath("$.requestId").isNotEmpty());
	}
}
