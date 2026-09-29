package com.betman.bet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betman.bet.dto.BetResponse;
import com.betman.bet.dto.PlaceBetRequest;
import com.betman.common.error.ErrorCode;
import com.betman.common.error.EventNotOpenException;
import com.betman.common.error.InsufficientBalanceException;
import com.betman.common.error.LimitExceededException;
import com.betman.common.error.NotFoundException;
import com.betman.config.LimitsProperties;
import com.betman.event.EventStatus;
import com.betman.event.Outcome;
import com.betman.event.Sport;
import com.betman.event.SportEvent;
import com.betman.event.SportEventService;
import com.betman.wallet.WalletService;
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
class BetServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-15T14:02:11Z");
	private static final Long USER_ID = 1L;

	@Mock
	private BetRepository betRepository;

	@Mock
	private SportEventService eventService;

	@Mock
	private WalletService walletService;

	private BetService service;

	private SportEvent event;

	@BeforeEach
	void setUp() {
		LimitsProperties limits = new LimitsProperties(bd("1.00"), bd("10000.00"), bd("10.00"), bd("50000.00"),
				bd("10.00"), bd("100.00"));
		service = new BetService(betRepository, eventService, walletService, limits, Clock.fixed(NOW, ZoneOffset.UTC));
		event = SportEvent.builder().id(12L).sport(Sport.FOOTBALL).homeTeam("Tubarões do Vale")
				.awayTeam("Leões da Serra").startsAt(NOW.plusSeconds(300)).status(EventStatus.SCHEDULED)
				.oddHome(bd("2.15")).oddDraw(bd("3.30")).oddAway(bd("3.10")).createdAt(NOW).build();
	}

	@Test
	void validBetDebitsStakeAndComputesPotentialPayout() {
		when(eventService.getOrThrow(12L)).thenReturn(event);
		when(walletService.getBalance(USER_ID)).thenReturn(bd("1000.00"));
		when(betRepository.save(any())).thenAnswer(inv -> {
			Bet b = inv.getArgument(0);
			b.setId(87L);
			return b;
		});
		when(walletService.debitStake(USER_ID, bd("25.00"), 87L)).thenReturn(bd("975.00"));

		BetResponse response = service.place(USER_ID, new PlaceBetRequest(12L, Outcome.HOME, bd("25")));

		assertThat(response.id()).isEqualTo(87L);
		assertThat(response.eventId()).isEqualTo(12L);
		assertThat(response.homeTeam()).isEqualTo("Tubarões do Vale");
		assertThat(response.awayTeam()).isEqualTo("Leões da Serra");
		assertThat(response.selection()).isEqualTo(Outcome.HOME);
		assertThat(response.stake()).isEqualByComparingTo("25.00");
		assertThat(response.odd()).isEqualByComparingTo("2.15");
		assertThat(response.potentialPayout()).isEqualByComparingTo("53.75");
		assertThat(response.status()).isEqualTo(BetStatus.PENDING);
		assertThat(response.placedAt()).isEqualTo(NOW);
		assertThat(response.walletBalance()).isEqualByComparingTo("975.00");

		ArgumentCaptor<Bet> saved = ArgumentCaptor.forClass(Bet.class);
		verify(betRepository).save(saved.capture());
		assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
		assertThat(saved.getValue().getOdd()).isEqualByComparingTo("2.15");
		verify(walletService).debitStake(USER_ID, bd("25.00"), 87L);
	}

	@Test
	void payoutIsRoundedHalfEven() {
		event.setOddAway(bd("3.33"));
		when(eventService.getOrThrow(12L)).thenReturn(event);
		when(walletService.getBalance(USER_ID)).thenReturn(bd("1000.00"));
		when(betRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(walletService.debitStake(any(), any(), any())).thenReturn(bd("0.00"));

		// 10.05 * 3.33 = 33.4665 -> 33.47
		BetResponse response = service.place(USER_ID, new PlaceBetRequest(12L, Outcome.AWAY, bd("10.05")));

		assertThat(response.potentialPayout()).isEqualByComparingTo("33.47");
	}

	@Test
	void insufficientBalanceIsRejectedBeforeSavingTheBet() {
		when(eventService.getOrThrow(12L)).thenReturn(event);
		when(walletService.getBalance(USER_ID)).thenReturn(bd("10.00"));

		assertThatThrownBy(() -> service.place(USER_ID, new PlaceBetRequest(12L, Outcome.HOME, bd("25.00"))))
				.isInstanceOf(InsufficientBalanceException.class)
				.hasMessage("Saldo de R$ 10,00 é menor que o valor da aposta de R$ 25,00.");
		verify(betRepository, never()).save(any());
		verify(walletService, never()).debitStake(any(), any(), any());
	}

	@Test
	void eventThatIsNotScheduledIsRejectedWithEventNotOpen() {
		event.setStatus(EventStatus.LIVE);
		when(eventService.getOrThrow(12L)).thenReturn(event);

		assertThatThrownBy(() -> service.place(USER_ID, new PlaceBetRequest(12L, Outcome.HOME, bd("25.00"))))
				.isInstanceOf(EventNotOpenException.class)
				.satisfies(ex -> assertThat(((EventNotOpenException) ex).getErrorCode())
						.isEqualTo(ErrorCode.EVENT_NOT_OPEN));
		verify(walletService, never()).getBalance(anyLong());
		verify(betRepository, never()).save(any());
	}

	@Test
	void stakeAboveMaximumIsRejectedWithLimitExceeded() {
		when(eventService.getOrThrow(12L)).thenReturn(event);

		assertThatThrownBy(() -> service.place(USER_ID, new PlaceBetRequest(12L, Outcome.DRAW, bd("10000.01"))))
				.isInstanceOf(LimitExceededException.class);
		verify(betRepository, never()).save(any());
	}

	@Test
	void stakeBelowMinimumIsRejectedWithLimitExceeded() {
		when(eventService.getOrThrow(12L)).thenReturn(event);

		assertThatThrownBy(() -> service.place(USER_ID, new PlaceBetRequest(12L, Outcome.DRAW, bd("0.99"))))
				.isInstanceOf(LimitExceededException.class);
	}

	@Test
	void betOfAnotherUserIsNotFound() {
		when(betRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.get(USER_ID, 5L)).isInstanceOf(NotFoundException.class);
	}

	private static BigDecimal bd(String value) {
		return new BigDecimal(value);
	}
}
