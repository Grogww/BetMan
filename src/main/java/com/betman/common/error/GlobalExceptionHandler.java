package com.betman.common.error;

import com.betman.common.web.RequestContextFilter;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every exception to an RFC 9457 {@link ProblemDetail} carrying {@code errorCode} and
 * {@code requestId}. Business rejections are logged at WARN, unexpected failures at ERROR.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	static final String ERROR_CODE = "errorCode";
	static final String REQUEST_ID = "requestId";
	static final String ERRORS = "errors";

	@ExceptionHandler(BetManException.class)
	public ProblemDetail handleBusiness(BetManException ex) {
		ErrorCode code = ex.getErrorCode();
		log.warn("Request rejected errorCode={} status={} detail={}", code, code.status().value(), ex.getMessage());
		ProblemDetail problem = problem(code.status(), code, code.title(), ex.getMessage());
		ex.customize(problem);
		return problem;
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
		ErrorCode code = ErrorCode.CONCURRENT_UPDATE;
		log.warn("Concurrent update detected errorCode={} message={}", code, ex.getMessage());
		return problem(code.status(), code, code.title(),
				"O registro foi alterado por outra operação. Tente novamente.");
	}

	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		ErrorCode code = ErrorCode.INTERNAL_ERROR;
		log.error("Unhandled exception errorCode={} type={}", code, ex.getClass().getName(), ex);
		return problem(code.status(), code, code.title(), "Ocorreu um erro inesperado. Tente novamente mais tarde.");
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldErrorDto> errors = ex.getBindingResult().getFieldErrors().stream()
				.map(fe -> new FieldErrorDto(fe.getField(), fe.getDefaultMessage()))
				.toList();
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
				ErrorCode.VALIDATION_ERROR.title(), "Um ou mais campos são inválidos.");
		problem.setProperty(ERRORS, errors);
		log.warn("Validation failed errorCode={} errors={}", ErrorCode.VALIDATION_ERROR, errors);
		return new ResponseEntity<>(problem, headers, HttpStatus.BAD_REQUEST);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
				ErrorCode.VALIDATION_ERROR.title(), "Corpo da requisição inválido ou mal formatado.");
		problem.setProperty(ERRORS, List.of(new FieldErrorDto("body", "JSON inválido ou campo com valor inaceitável")));
		log.warn("Unreadable request body errorCode={} message={}", ErrorCode.VALIDATION_ERROR,
				ex.getMostSpecificCause().getMessage());
		return new ResponseEntity<>(problem, headers, HttpStatus.BAD_REQUEST);
	}

	/**
	 * Every other exception handled by {@link ResponseEntityExceptionHandler} (type mismatch,
	 * missing parameter, unsupported method, unknown route...) passes through here.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ProblemDetail problem = body instanceof ProblemDetail pd ? pd : ProblemDetail.forStatus(statusCode);
		HttpStatus status = HttpStatus.resolve(statusCode.value());
		ErrorCode code = switch (statusCode.value()) {
			case 400 -> ErrorCode.VALIDATION_ERROR;
			case 404 -> ErrorCode.NOT_FOUND;
			default -> null;
		};
		if (code != null) {
			problem.setTitle(code.title());
			problem.setProperty(ERROR_CODE, code.name());
			if (code == ErrorCode.VALIDATION_ERROR) {
				problem.setProperty(ERRORS, List.of(new FieldErrorDto("request", problem.getDetail())));
			}
		} else {
			problem.setProperty(ERROR_CODE, status != null ? status.name() : "HTTP_" + statusCode.value());
		}
		problem.setProperty(REQUEST_ID, MDC.get(RequestContextFilter.REQUEST_ID));
		log.warn("Request failed errorCode={} status={} detail={}", problem.getProperties().get(ERROR_CODE),
				statusCode.value(), problem.getDetail());
		return new ResponseEntity<>(problem, headers, statusCode);
	}

	private static ProblemDetail problem(HttpStatus status, ErrorCode code, String title, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setProperty(ERROR_CODE, code.name());
		problem.setProperty(REQUEST_ID, MDC.get(RequestContextFilter.REQUEST_ID));
		return problem;
	}
}
