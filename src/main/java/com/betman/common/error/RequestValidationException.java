package com.betman.common.error;

import java.util.List;
import org.springframework.http.ProblemDetail;

/** 400 VALIDATION_ERROR raised by application code (e.g. missing X-User-Id header). */
public class RequestValidationException extends BetManException {

	private final List<FieldErrorDto> errors;

	public RequestValidationException(String detail, List<FieldErrorDto> errors) {
		super(ErrorCode.VALIDATION_ERROR, detail);
		this.errors = List.copyOf(errors);
	}

	public RequestValidationException(String field, String message) {
		this(message, List.of(new FieldErrorDto(field, message)));
	}

	public List<FieldErrorDto> getErrors() {
		return errors;
	}

	@Override
	protected void customize(ProblemDetail problemDetail) {
		problemDetail.setProperty("errors", errors);
	}
}
