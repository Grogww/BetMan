package com.betman.wallet.dto;

import com.betman.wallet.WalletTransaction;
import com.betman.wallet.WalletTransactionType;
import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransactionResponse(
		Long id,
		WalletTransactionType type,
		boolean credit,
		BigDecimal amount,
		BigDecimal balanceAfter,
		Long referenceId,
		Instant createdAt) {

	public static WalletTransactionResponse from(WalletTransaction tx) {
		return new WalletTransactionResponse(tx.getId(), tx.getType(), tx.getType().isCredit(), tx.getAmount(),
				tx.getBalanceAfter(), tx.getReferenceId(), tx.getCreatedAt());
	}
}
