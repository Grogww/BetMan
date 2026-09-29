package com.betman.wallet;

import com.betman.common.web.CurrentUser;
import com.betman.common.web.PageResponse;
import com.betman.common.web.Paging;
import com.betman.wallet.dto.AmountRequest;
import com.betman.wallet.dto.WalletResponse;
import com.betman.wallet.dto.WalletTransactionResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

	private final WalletService walletService;

	public WalletController(WalletService walletService) {
		this.walletService = walletService;
	}

	@GetMapping
	public WalletResponse get(@CurrentUser Long userId) {
		return walletService.get(userId);
	}

	@PostMapping("/deposit")
	public WalletResponse deposit(@CurrentUser Long userId, @Valid @RequestBody AmountRequest request) {
		return walletService.deposit(userId, request.amount());
	}

	@PostMapping("/withdraw")
	public WalletResponse withdraw(@CurrentUser Long userId, @Valid @RequestBody AmountRequest request) {
		return walletService.withdraw(userId, request.amount());
	}

	@GetMapping("/transactions")
	public PageResponse<WalletTransactionResponse> transactions(@CurrentUser Long userId,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = Paging.DEFAULT_SIZE) int size) {
		return walletService.transactions(userId, Paging.of(page, size, Sort.by("createdAt", "id").descending()));
	}
}
