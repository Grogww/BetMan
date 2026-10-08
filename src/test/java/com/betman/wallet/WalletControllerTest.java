package com.betman.wallet;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betman.common.error.InsufficientBalanceException;
import com.betman.common.error.NotFoundException;
import com.betman.support.MetricsTestConfiguration;
import com.betman.user.UserService;
import com.betman.wallet.dto.WalletResponse;
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
@WebMvcTest(WalletController.class)
class WalletControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WalletService walletService;

	@MockitoBean
	private UserService userService;

	@Test
	void getWalletReturnsBalanceAndEchoesRequestId() throws Exception {
		when(walletService.get(1L))
				.thenReturn(new WalletResponse(1L, new BigDecimal("975.00"), Instant.parse("2026-09-15T14:02:11Z")));

		mockMvc.perform(get("/api/wallet").header("X-User-Id", "1").header("X-Request-Id", "abc-123"))
				.andExpect(status().isOk())
				.andExpect(header().string("X-Request-Id", "abc-123"))
				.andExpect(jsonPath("$.userId").value(1))
				.andExpect(jsonPath("$.balance").value(975.00));
	}

	@Test
	void missingUserHeaderIsBadRequestWithValidationError() throws Exception {
		mockMvc.perform(get("/api/wallet"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.requestId").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].field").value("X-User-Id"));
	}

	@Test
	void nonNumericUserHeaderIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/wallet").header("X-User-Id", "abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}

	@Test
	void unknownUserIsNotFound() throws Exception {
		doThrow(NotFoundException.user(42L)).when(userService).ensureExists(42L);

		mockMvc.perform(get("/api/wallet").header("X-User-Id", "42"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("NOT_FOUND"))
				.andExpect(jsonPath("$.title").value("Recurso não encontrado"));
	}

	@Test
	void withdrawAboveBalanceIsUnprocessableWithProblemDetail() throws Exception {
		when(walletService.withdraw(eq(1L), any()))
				.thenThrow(new InsufficientBalanceException(new BigDecimal("10.00"), new BigDecimal("25.00"),
						"o valor do saque"));

		mockMvc.perform(post("/api/wallet/withdraw").header("X-User-Id", "1")
						.contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 25.00}"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(header().string("Content-Type", "application/problem+json"))
				.andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_BALANCE"))
				.andExpect(jsonPath("$.title").value("Saldo insuficiente"))
				.andExpect(jsonPath("$.detail").value("Saldo de R$ 10,00 é menor que o valor do saque de R$ 25,00."))
				.andExpect(jsonPath("$.instance").value("/api/wallet/withdraw"))
				.andExpect(jsonPath("$.status").value(422));
	}

	@Test
	void depositWithNegativeAmountIsBadRequestWithFieldErrors() throws Exception {
		mockMvc.perform(post("/api/wallet/deposit").header("X-User-Id", "1")
						.contentType(MediaType.APPLICATION_JSON).content("{\"amount\": -5}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors", hasSize(1)))
				.andExpect(jsonPath("$.errors[0].field").value("amount"));
	}

	@Test
	void malformedJsonIsBadRequest() throws Exception {
		mockMvc.perform(post("/api/wallet/deposit").header("X-User-Id", "1")
						.contentType(MediaType.APPLICATION_JSON).content("{\"amount\": "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}
}
