package com.betman.common.error;

import org.springframework.http.ProblemDetail;

/**
 * Base class of every business exception. Carries the {@link ErrorCode} (which defines the HTTP
 * status and the title) and a user-facing detail message in pt-BR.
 */
public abstract class BetManException extends RuntimeException {

	private final ErrorCode errorCode;

	protected BetManException(ErrorCode errorCode, String detail) {
		super(detail);
		this.errorCode = errorCode;
	}

	public ErrorCode getErrorCode() {
		return errorCode;
	}

	/** Hook for subclasses that need to add extra properties to the ProblemDetail. */
	protected void customize(ProblemDetail problemDetail) {
	}
}
