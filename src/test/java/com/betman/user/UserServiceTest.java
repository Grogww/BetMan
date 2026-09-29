package com.betman.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betman.common.error.NotFoundException;
import com.betman.common.error.UsernameTakenException;
import com.betman.user.dto.CreateUserRequest;
import com.betman.user.dto.UserResponse;
import com.betman.wallet.Wallet;
import com.betman.wallet.WalletService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");

	@Mock
	private UserRepository userRepository;

	@Mock
	private WalletService walletService;

	private UserService service;

	@BeforeEach
	void setUp() {
		service = new UserService(userRepository, walletService, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void createPersistsUserAndOpensWalletWithWelcomeBonus() {
		when(userRepository.existsByUsername("bruce")).thenReturn(false);
		when(userRepository.save(any())).thenAnswer(inv -> {
			User u = inv.getArgument(0);
			u.setId(5L);
			return u;
		});
		when(walletService.createWallet(5L))
				.thenReturn(Wallet.builder().id(1L).userId(5L).balance(new BigDecimal("100.00")).build());

		UserResponse response = service.create(new CreateUserRequest("bruce"));

		assertThat(response.id()).isEqualTo(5L);
		assertThat(response.username()).isEqualTo("bruce");
		assertThat(response.createdAt()).isEqualTo(NOW);
		verify(walletService).createWallet(5L);
	}

	@Test
	void duplicateUsernameIsRejected() {
		when(userRepository.existsByUsername("demo")).thenReturn(true);

		assertThatThrownBy(() -> service.create(new CreateUserRequest("demo")))
				.isInstanceOf(UsernameTakenException.class);
		verify(userRepository, never()).save(any());
		verify(walletService, never()).createWallet(any());
	}

	@Test
	void ensureExistsThrowsForUnknownUser() {
		when(userRepository.existsById(404L)).thenReturn(false);

		assertThatThrownBy(() -> service.ensureExists(404L)).isInstanceOf(NotFoundException.class);
	}
}
