package com.betman.common.error;

public class EventAlreadySettledException extends BetManException {

	public EventAlreadySettledException(Long eventId) {
		super(ErrorCode.EVENT_ALREADY_SETTLED, "Evento " + eventId + " já foi finalizado e liquidado.");
	}
}
