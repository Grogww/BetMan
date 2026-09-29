package com.betman.user;

import com.betman.common.web.PageResponse;
import com.betman.common.web.Paging;
import com.betman.user.dto.CreateUserRequest;
import com.betman.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
		return userService.create(request);
	}

	@GetMapping
	public PageResponse<UserResponse> list(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = Paging.DEFAULT_SIZE) int size) {
		return userService.list(Paging.of(page, size, Sort.by("id").ascending()));
	}

	@GetMapping("/{id}")
	public UserResponse get(@PathVariable Long id) {
		return userService.get(id);
	}
}
