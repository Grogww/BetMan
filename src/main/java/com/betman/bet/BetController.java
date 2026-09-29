package com.betman.bet;

import com.betman.bet.dto.BetResponse;
import com.betman.bet.dto.PlaceBetRequest;
import com.betman.common.web.CurrentUser;
import com.betman.common.web.PageResponse;
import com.betman.common.web.Paging;
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
@RequestMapping("/api/bets")
public class BetController {

	private final BetService betService;

	public BetController(BetService betService) {
		this.betService = betService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public BetResponse place(@CurrentUser Long userId, @Valid @RequestBody PlaceBetRequest request) {
		return betService.place(userId, request);
	}

	@GetMapping
	public PageResponse<BetResponse> list(@CurrentUser Long userId,
			@RequestParam(required = false) BetStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = Paging.DEFAULT_SIZE) int size) {
		return betService.list(userId, status, Paging.of(page, size, Sort.by("placedAt", "id").descending()));
	}

	@GetMapping("/{id}")
	public BetResponse get(@CurrentUser Long userId, @PathVariable Long id) {
		return betService.get(userId, id);
	}
}
