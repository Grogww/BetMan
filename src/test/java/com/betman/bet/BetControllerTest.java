package com.betman.bet;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betman.bet.dto.BetResponse;
import com.betman.bet.dto.PlaceBetRequest;
import com.betman.common.error.EventNotOpenException;
import com.betman.common.error.InsufficientBalanceException;
import com.betman.common.error.NotFoundException;
import com.betman.event.Outcome;
import com.betman.user.UserService;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BetController.class)
class BetControllerTest {

	private static final String VALID_BODY = "{\"eventId\": 12, \"selection\": \"HOME\", \"stake\": 25.00}";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BetService betService;

	@MockitoBean
	private UserService userService;

	@Test
	void placeBetReturnsCreatedWithBetAndBalance() throws Exception {
		when(betService.place(eq(1L), any())).thenReturn(new BetResponse(87L, 12L, "Tubarões do Vale",
				"Leões da Serra", Outcome.HOME, new BigDecimal("25.00"), new BigDecimal("2.15"),
				new BigDecimal("53.75"), BetStatus.PENDING, Instant.parse("2026-09-15T14:02:11Z"), null,
				new BigDecimal("975.00")));

		mockMvc.perform(post("/api/bets").header("X-User-Id", "1").contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isCreated())
				.andExpect(header().exists("X-Request-Id"))
				.andExpect(jsonPath("$.id").value(87))
				.andExpect(jsonPath("$.eventId").value(12))
				.andExpect(jsonPath("$.selection").value("HOME"))
				.andExpect(jsonPath("$.stake").value(25.00))
				.andExpect(jsonPath("$.odd").value(2.15))
				.andExpect(jsonPath("$.potentialPayout").value(53.75))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.placedAt").value("2026-09-15T14:02:11Z"))
				.andExpect(jsonPath("$.walletBalance").value(975.00));

		ArgumentCaptor<PlaceBetRequest> request = ArgumentCaptor.forClass(PlaceBetRequest.class);
		verify(betService).place(eq(1L), request.capture());
		org.assertj.core.api.Assertions.assertThat(request.getValue().stake()).isEqualByComparingTo("25.00");
	}

	@Test
	void invalidBodyIsBadRequestWithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/bets").header("X-User-Id", "1").contentType(MediaType.APPLICATION_JSON)
						.content("{\"selection\": \"HOME\", \"stake\": -1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.title").value("Requisição inválida"))
				.andExpect(jsonPath("$.errors[*].field").value(hasItems("eventId", "stake")));
		verify(betService, never()).place(any(), any());
	}

	@Test
	void unknownSelectionIsBadRequest() throws Exception {
		mockMvc.perform(post("/api/bets").header("X-User-Id", "1").contentType(MediaType.APPLICATION_JSON)
						.content("{\"eventId\": 12, \"selection\": \"BOTH\", \"stake\": 25.00}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	@Test
	void missingUserHeaderIsBadRequest() throws Exception {
		mockMvc.perform(post("/api/bets").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("X-User-Id"));
		verify(betService, never()).place(any(), any());
	}

	@Test
	void insufficientBalanceIsUnprocessableWithErrorCode() throws Exception {
		when(betService.place(eq(1L), any())).thenThrow(new InsufficientBalanceException(new BigDecimal("10.00"),
				new BigDecimal("25.00"), "o valor da aposta"));

		mockMvc.perform(post("/api/bets").header("X-User-Id", "1").contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.title").value("Saldo insuficiente"))
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.detail").value("Saldo de R$ 10,00 é menor que o valor da aposta de R$ 25,00."))
				.andExpect(jsonPath("$.instance").value("/api/bets"))
				.andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"))
				.andExpect(jsonPath("$.requestId").isNotEmpty());
	}

	@Test
	void closedEventIsConflictWithErrorCode() throws Exception {
		when(betService.place(eq(1L), any())).thenThrow(new EventNotOpenException(12L, "LIVE"));

		mockMvc.perform(post("/api/bets").header("X-User-Id", "1").contentType(MediaType.APPLICATION_JSON)
						.content(VALID_BODY))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("EVENT_NOT_OPEN"));
	}

	@Test
	void betOfAnotherUserIsNotFound() throws Exception {
		when(betService.get(1L, 5L)).thenThrow(NotFoundException.bet(5L));

		mockMvc.perform(get("/api/bets/5").header("X-User-Id", "1"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("NOT_FOUND"));
	}
}
