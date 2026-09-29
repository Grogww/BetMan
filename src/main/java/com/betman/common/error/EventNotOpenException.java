package com.betman.common.error;

public class EventNotOpenException extends BetManException {

	public EventNotOpenException(Long eventId, String status) {
		super(ErrorCode.EVENT_NOT_OPEN,
				"Evento " + eventId + " não está aberto para apostas (status atual: " + status + ").");
	}
}
