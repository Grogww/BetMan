package com.betman.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betman.bet.BetController;
import com.betman.bet.BetService;
import com.betman.support.MetricsTestConfiguration;
import com.betman.user.UserService;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Every error response must be counted exactly once in {@code betman.errors}, tagged by errorCode. */
@Import(MetricsTestConfiguration.class)
@WebMvcTest(BetController.class)
class GlobalExceptionHandlerMetricsTest {

	private static final String VALID_BODY = "{\"eventId\": 12, \"selection\": \"HOME\", \"stake\": 25.00}";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private MeterRegistry registry;

	@MockitoBean
	private BetService betService;

	@MockitoBean
	private UserService userService;

	@BeforeEach
	void clearMetrics() {
		registry.clear();
	}

	@Test
	void businessErrorIsCountedByItsErrorCode() throws Exception {
		when(betService.place(eq(1L), any())).thenThrow(new InsufficientBalanceException(new BigDecimal("10.00"),
				new BigDecimal("25.00"), "o valor da aposta"));

		placeBet(VALID_BODY).andExpect(status().isUnprocessableContent());
		placeBet(VALID_BODY).andExpect(status().isUnprocessableContent());

		assertThat(errors("INSUFFICIENT_BALANCE")).isEqualTo(2.0);
	}

	@Test
	void invalidBodyIsCountedOnceAsValidationError() throws Exception {
		placeBet("{\"selection\": \"HOME\", \"stake\": -1}").andExpect(status().isBadRequest());

		assertThat(errors("VALIDATION_ERROR")).isEqualTo(1.0);
		assertThat(registry.get(GlobalExceptionHandler.ERRORS_METRIC).counters()).hasSize(1);
	}

	@Test
	void unexpectedExceptionIsCountedAsInternalError() throws Exception {
		when(betService.place(eq(1L), any())).thenThrow(new IllegalStateException("boom"));

		placeBet(VALID_BODY).andExpect(status().isInternalServerError());

		assertThat(errors("INTERNAL_ERROR")).isEqualTo(1.0);
	}

	@Test
	void frameworkErrorWithoutErrorCodeIsCountedByHttpStatusName() throws Exception {
		mockMvc.perform(put("/api/bets").header("X-User-Id", "1")).andExpect(status().isMethodNotAllowed());

		assertThat(errors("METHOD_NOT_ALLOWED")).isEqualTo(1.0);
	}

	private ResultActions placeBet(String body) throws Exception {
		return mockMvc.perform(post("/api/bets").header("X-User-Id", "1").contentType(MediaType.APPLICATION_JSON)
				.content(body));
	}

	private double errors(String errorCode) {
		return registry.get(GlobalExceptionHandler.ERRORS_METRIC).tag("errorCode", errorCode).counter().count();
	}
}
