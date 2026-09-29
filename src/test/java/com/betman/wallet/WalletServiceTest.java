package com.betman.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betman.common.error.ErrorCode;
import com.betman.common.error.InsufficientBalanceException;
import com.betman.common.error.LimitExceededException;
import com.betman.common.error.NotFoundException;
import com.betman.config.LimitsProperties;
import com.betman.wallet.dto.WalletResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
	private static final Long USER_ID = 7L;

	@Mock
	private WalletRepository walletRepository;

	@Mock
	private WalletTransactionRepository transactionRepository;

	private WalletService service;

	private Wallet wallet;

	@BeforeEach
	void setUp() {
		LimitsProperties limits = new LimitsProperties(bd("1.00"), bd("10000.00"), bd("10.00"), bd("50000.00"),
				bd("10.00"), bd("100.00"));
		service = new WalletService(walletRepository, transactionRepository, limits, Clock.fixed(NOW, ZoneOffset.UTC));
		wallet = Wallet.builder().id(3L).userId(USER_ID).balance(bd("50.00")).version(0L)
				.updatedAt(NOW.minusSeconds(3600)).build();
	}

	@Test
	void depositWithinLimitsCreditsBalanceAndRecordsOneTransaction() {
		when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));
		when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		WalletResponse response = service.deposit(USER_ID, bd("25.5"));

		assertThat(response.balance()).isEqualByComparingTo("75.50");
		assertThat(wallet.getUpdatedAt()).isEqualTo(NOW);

		ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
		verify(transactionRepository).save(captor.capture());
		WalletTransaction tx = captor.getValue();
		assertThat(tx.getWalletId()).isEqualTo(3L);
		assertThat(tx.getType()).isEqualTo(WalletTransactionType.DEPOSIT);
		assertThat(tx.getAmount()).isEqualByComparingTo("25.50");
		assertThat(tx.getBalanceAfter()).isEqualByComparingTo("75.50");
		assertThat(tx.getReferenceId()).isNull();
		assertThat(tx.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void depositBelowMinimumIsRejectedWithLimitExceeded() {
		assertThatThrownBy(() -> service.deposit(USER_ID, bd("9.99")))
				.isInstanceOf(LimitExceededException.class)
				.satisfies(ex -> assertThat(((LimitExceededException) ex).getErrorCode())
						.isEqualTo(ErrorCode.LIMIT_EXCEEDED));
		verify(walletRepository, never()).save(any());
		verify(transactionRepository, never()).save(any());
	}

	@Test
	void depositAboveMaximumIsRejectedWithLimitExceeded() {
		assertThatThrownBy(() -> service.deposit(USER_ID, bd("50000.01")))
				.isInstanceOf(LimitExceededException.class);
		verify(transactionRepository, never()).save(any());
	}

	@Test
	void withdrawAboveBalanceIsRejectedWithInsufficientBalance() {
		when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));

		assertThatThrownBy(() -> service.withdraw(USER_ID, bd("50.01")))
				.isInstanceOf(InsufficientBalanceException.class)
				.hasMessage("Saldo de R$ 50,00 é menor que o valor do saque de R$ 50,01.");
		assertThat(wallet.getBalance()).isEqualByComparingTo("50.00");
		verify(transactionRepository, never()).save(any());
	}

	@Test
	void withdrawBelowMinimumIsRejectedWithLimitExceeded() {
		assertThatThrownBy(() -> service.withdraw(USER_ID, bd("5.00")))
				.isInstanceOf(LimitExceededException.class);
	}

	@Test
	void withdrawWithinBalanceDebitsAndRecordsTransaction() {
		when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));
		when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		WalletResponse response = service.withdraw(USER_ID, bd("20.00"));

		assertThat(response.balance()).isEqualByComparingTo("30.00");
		ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
		verify(transactionRepository).save(captor.capture());
		assertThat(captor.getValue().getType()).isEqualTo(WalletTransactionType.WITHDRAW);
		assertThat(captor.getValue().getAmount()).isEqualByComparingTo("20.00");
	}

	@Test
	void createWalletCreditsWelcomeBonusAsSingleTransaction() {
		when(walletRepository.save(any())).thenAnswer(inv -> {
			Wallet w = inv.getArgument(0);
			w.setId(99L);
			return w;
		});
		when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		Wallet created = service.createWallet(USER_ID);

		assertThat(created.getBalance()).isEqualByComparingTo("100.00");
		ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
		verify(transactionRepository).save(captor.capture());
		assertThat(captor.getValue().getType()).isEqualTo(WalletTransactionType.WELCOME_BONUS);
		assertThat(captor.getValue().getAmount()).isEqualByComparingTo("100.00");
		assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("100.00");
	}

	@Test
	void debitStakeRecordsBetReference() {
		when(walletRepository.findByUserId(USER_ID)).thenReturn(Optional.of(wallet));
		when(walletRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		BigDecimal balance = service.debitStake(USER_ID, bd("15.00"), 42L);

		assertThat(balance).isEqualByComparingTo("35.00");
		ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
		verify(transactionRepository).save(captor.capture());
		assertThat(captor.getValue().getType()).isEqualTo(WalletTransactionType.BET_STAKE);
		assertThat(captor.getValue().getReferenceId()).isEqualTo(42L);
	}

	@Test
	void unknownUserYieldsNotFound() {
		when(walletRepository.findByUserId(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(999L)).isInstanceOf(NotFoundException.class);
	}

	private static BigDecimal bd(String value) {
		return new BigDecimal(value);
	}
}
