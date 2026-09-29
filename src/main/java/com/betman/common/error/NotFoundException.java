package com.betman.common.error;

public class NotFoundException extends BetManException {

	public NotFoundException(String detail) {
		super(ErrorCode.NOT_FOUND, detail);
	}

	public static NotFoundException user(Long userId) {
		return new NotFoundException("Usuário " + userId + " não encontrado.");
	}

	public static NotFoundException event(Long eventId) {
		return new NotFoundException("Evento " + eventId + " não encontrado.");
	}

	public static NotFoundException bet(Long betId) {
		return new NotFoundException("Aposta " + betId + " não encontrada.");
	}
}
