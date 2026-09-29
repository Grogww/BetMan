package com.betman.wallet.dto;

import com.betman.wallet.Wallet;
import java.math.BigDecimal;
import java.time.Instant;

public record WalletResponse(Long userId, BigDecimal balance, Instant updatedAt) {

	public static WalletResponse from(Wallet wallet) {
		return new WalletResponse(wallet.getUserId(), wallet.getBalance(), wallet.getUpdatedAt());
	}
}
