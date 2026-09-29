package com.betman.common.error;

import com.betman.common.money.Money;
import java.math.BigDecimal;

public class InsufficientBalanceException extends BetManException {

	/**
	 * @param operation description of the amount in pt-BR, e.g. "o valor da aposta" or "o valor do saque"
	 */
	public InsufficientBalanceException(BigDecimal balance, BigDecimal amount, String operation) {
		super(ErrorCode.INSUFFICIENT_BALANCE, "Saldo de " + Money.format(balance) + " é menor que " + operation
				+ " de " + Money.format(amount) + ".");
	}
}
