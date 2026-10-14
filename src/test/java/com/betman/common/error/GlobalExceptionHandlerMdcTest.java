package com.betman.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.betman.bet.BetController;
import com.betman.bet.BetService;
import com.betman.common.web.RequestContextFilter;
import com.betman.support.MetricsTestConfiguration;
import com.betman.user.UserService;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Error logs must carry {@code errorCode} and {@code requestId} in the MDC, since the GELF appender
 * turns MDC entries into Graylog fields. The MDC must be clean once the request ends.
 */
@Import(MetricsTestConfiguration.class)
@WebMvcTest(BetController.class)
class GlobalExceptionHandlerMdcTest {

	private static final String VALID_BODY = "{\"eventId\": 12, \"selection\": \"HOME\", \"stake\": 25.00}";
	private static final String REQUEST_ID = "req-t21-0001";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BetService betService;

	@MockitoBean
	private UserService userService;

	private final Logger handlerLogger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	@BeforeEach
	void captureLogs() {
		appender.start();
		handlerLogger.addAppender(appender);
	}

	@AfterEach
	void releaseLogs() {
		handlerLogger.detachAppender(appender);
	}

	@Test
	void businessErrorIsLoggedAtWarnWithErrorCodeAndRequestId() throws Exception {
		when(betService.place(eq(1L), any())).thenThrow(new InsufficientBalanceException(new BigDecimal("10.00"),
				new BigDecimal("25.00"), "o valor da aposta"));

		placeBet(VALID_BODY).andExpect(status().isUnprocessableContent());

		ILoggingEvent event = singleEvent();
		assertThat(event.getLevel()).isEqualTo(Level.WARN);
		assertThat(event.getMDCPropertyMap())
				.containsEntry(RequestContextFilter.ERROR_CODE, "INSUFFICIENT_BALANCE")
				.containsEntry(RequestContextFilter.REQUEST_ID, REQUEST_ID);
	}

	@Test
	void invalidBodyIsLoggedWithValidationErrorCode() throws Exception {
		placeBet("{\"selection\": \"HOME\", \"stake\": -1}").andExpect(status().isBadRequest());

		assertThat(singleEvent().getMDCPropertyMap()).containsEntry(RequestContextFilter.ERROR_CODE,
				"VALIDATION_ERROR");
	}

	@Test
	void unexpectedExceptionIsLoggedAtErrorWithInternalErrorCode() throws Exception {
		when(betService.place(eq(1L), any())).thenThrow(new IllegalStateException("boom"));

		placeBet(VALID_BODY).andExpect(status().isInternalServerError());

		ILoggingEvent event = singleEvent();
		assertThat(event.getLevel()).isEqualTo(Level.ERROR);
		assertThat(event.getMDCPropertyMap()).containsEntry(RequestContextFilter.ERROR_CODE, "INTERNAL_ERROR");
	}

	@Test
	void frameworkErrorIsLoggedWithHttpStatusNameAsErrorCode() throws Exception {
		mockMvc.perform(put("/api/bets").header("X-User-Id", "1")).andExpect(status().isMethodNotAllowed());

		assertThat(singleEvent().getMDCPropertyMap()).containsEntry(RequestContextFilter.ERROR_CODE,
				"METHOD_NOT_ALLOWED");
	}

	@Test
	void errorCodeIsClearedWhenRequestEnds() throws Exception {
		when(betService.place(eq(1L), any())).thenThrow(new IllegalStateException("boom"));

		placeBet(VALID_BODY).andExpect(status().isInternalServerError());

		assertThat(MDC.get(RequestContextFilter.ERROR_CODE)).isNull();
		assertThat(MDC.get(RequestContextFilter.REQUEST_ID)).isNull();
	}

	private ResultActions placeBet(String body) throws Exception {
		return mockMvc.perform(post("/api/bets").header("X-User-Id", "1")
				.header(RequestContextFilter.REQUEST_ID_HEADER, REQUEST_ID)
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ILoggingEvent singleEvent() {
		assertThat(appender.list).hasSize(1);
		return appender.list.get(0);
	}
}
