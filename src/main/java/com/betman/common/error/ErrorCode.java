package com.betman.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable machine-readable codes returned in the {@code errorCode} property of every ProblemDetail.
 */
public enum ErrorCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Requisição inválida"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado"),
	USERNAME_TAKEN(HttpStatus.CONFLICT, "Nome de usuário já utilizado"),
	EVENT_NOT_OPEN(HttpStatus.CONFLICT, "Evento não aberto para apostas"),
	EVENT_ALREADY_SETTLED(HttpStatus.CONFLICT, "Evento já finalizado"),
	CONCURRENT_UPDATE(HttpStatus.CONFLICT, "Conflito de atualização"),
	INSUFFICIENT_BALANCE(HttpStatus.UNPROCESSABLE_CONTENT, "Saldo insuficiente"),
	LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT, "Valor fora dos limites"),
	ODDS_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Provedor de odds indisponível"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");

	private final HttpStatus status;
	private final String title;

	ErrorCode(HttpStatus status, String title) {
		this.status = status;
		this.title = title;
	}

	public HttpStatus status() {
		return status;
	}

	public String title() {
		return title;
	}
}
