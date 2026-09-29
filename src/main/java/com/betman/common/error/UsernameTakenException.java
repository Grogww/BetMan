package com.betman.common.error;

public class UsernameTakenException extends BetManException {

	public UsernameTakenException(String username) {
		super(ErrorCode.USERNAME_TAKEN, "O nome de usuário '" + username + "' já está em uso.");
	}
}
