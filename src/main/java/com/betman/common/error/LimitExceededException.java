package com.betman.common.error;

public class LimitExceededException extends BetManException {

	public LimitExceededException(String detail) {
		super(ErrorCode.LIMIT_EXCEEDED, detail);
	}
}
