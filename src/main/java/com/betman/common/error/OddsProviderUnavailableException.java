package com.betman.common.error;

public class OddsProviderUnavailableException extends BetManException {

	public OddsProviderUnavailableException(Long eventId) {
		super(ErrorCode.ODDS_PROVIDER_UNAVAILABLE,
				"O provedor de odds ao vivo não respondeu para o evento " + eventId + ". Tente novamente.");
	}
}
