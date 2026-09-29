package com.betman.user.dto;

import com.betman.user.User;
import java.time.Instant;

public record UserResponse(Long id, String username, Instant createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getUsername(), user.getCreatedAt());
	}
}
