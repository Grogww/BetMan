package com.betman.wallet;

/**
 * Kind of wallet movement. The type decides whether the amount is credited or debited.
 */
public enum WalletTransactionType {

	WELCOME_BONUS(true),
	DEPOSIT(true),
	WITHDRAW(false),
	BET_STAKE(false),
	BET_PAYOUT(true);

	private final boolean credit;

	WalletTransactionType(boolean credit) {
		this.credit = credit;
	}

	public boolean isCredit() {
		return credit;
	}
}
