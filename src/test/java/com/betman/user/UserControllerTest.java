package com.betman.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.betman.common.error.UsernameTakenException;
import com.betman.support.MetricsTestConfiguration;
import com.betman.user.dto.UserResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(MetricsTestConfiguration.class)
@WebMvcTest(UserController.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	void createReturnsCreated() throws Exception {
		when(userService.create(any()))
				.thenReturn(new UserResponse(2L, "bruce", Instant.parse("2026-09-15T12:00:00Z")));

		mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"bruce\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(2))
				.andExpect(jsonPath("$.username").value("bruce"));
	}

	@Test
	void invalidUsernameIsBadRequest() throws Exception {
		mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"a b\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("username"));
	}

	@Test
	void duplicateUsernameIsConflict() throws Exception {
		when(userService.create(any())).thenThrow(new UsernameTakenException("demo"));

		mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\": \"demo\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("USERNAME_TAKEN"));
	}

	@Test
	void nonNumericIdIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/users/abc"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
	}
}
