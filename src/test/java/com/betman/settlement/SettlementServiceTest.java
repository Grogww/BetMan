package com.betman.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.betman.bet.Bet;
import com.betman.bet.BetRepository;
import com.betman.bet.BetStatus;
import com.betman.event.EventStatus;
import com.betman.event.Outcome;
import com.betman.event.SportEvent;
import com.betman.wallet.WalletService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-15T14:10:00Z");

	@Mock
	private BetRepository betRepository;

	@Mock
	private WalletService walletService;

	private SettlementService service;

	private SportEvent event;

	@BeforeEach
	void setUp() {
		service = new SettlementService(betRepository, walletService, Clock.fixed(NOW, ZoneOffset.UTC));
		event = SportEvent.builder().id(12L).homeTeam("Tubarões do Vale").awayTeam("Leões da Serra")
				.status(EventStatus.FINISHED).result(Outcome.HOME).finishedAt(NOW).build();
	}

	@Test
	void winningBetIsPaidAndMarkedWon() {
		Bet winner = pendingBet(1L, 7L, Outcome.HOME, "25.00", "53.75");
		when(betRepository.findByEventIdAndStatus(12L, BetStatus.PENDING)).thenReturn(List.of(winner));

		SettlementSummary summary = service.settleEvent(event);

		assertThat(winner.getStatus()).isEqualTo(BetStatus.WON);
		assertThat(winner.getSettledAt()).isEqualTo(NOW);
		verify(walletService).creditPayout(7L, new BigDecimal("53.75"), 1L);
		verify(betRepository).save(winner);
		assertThat(summary.won()).isEqualTo(1);
		assertThat(summary.lost()).isZero();
		assertThat(summary.paid()).isEqualByComparingTo("53.75");
	}

	@Test
	void losingBetIsMarkedLostWithoutWalletMovement() {
		Bet loser = pendingBet(2L, 8L, Outcome.AWAY, "10.00", "31.00");
		when(betRepository.findByEventIdAndStatus(12L, BetStatus.PENDING)).thenReturn(List.of(loser));

		SettlementSummary summary = service.settleEvent(event);

		assertThat(loser.getStatus()).isEqualTo(BetStatus.LOST);
		assertThat(loser.getSettledAt()).isEqualTo(NOW);
		verify(walletService, never()).creditPayout(anyLong(), any(), anyLong());
		assertThat(summary.won()).isZero();
		assertThat(summary.lost()).isEqualTo(1);
		assertThat(summary.paid()).isEqualByComparingTo("0.00");
	}

	@Test
	void settlementIsIdempotent() {
		Bet winner = pendingBet(1L, 7L, Outcome.HOME, "25.00", "53.75");
		Bet loser = pendingBet(2L, 8L, Outcome.DRAW, "5.00", "16.50");
		// the repository returns the same (already mutated) instances on the second run
		when(betRepository.findByEventIdAndStatus(12L, BetStatus.PENDING)).thenReturn(List.of(winner, loser));

		SettlementSummary first = service.settleEvent(event);
		SettlementSummary second = service.settleEvent(event);

		assertThat(first.won()).isEqualTo(1);
		assertThat(first.lost()).isEqualTo(1);
		assertThat(second.won()).isZero();
		assertThat(second.lost()).isZero();
		assertThat(second.paid()).isEqualByComparingTo("0.00");
		verify(walletService, times(1)).creditPayout(7L, new BigDecimal("53.75"), 1L);
		verify(betRepository, times(2)).save(any());
	}

	@Test
	void eventWithoutResultCannotBeSettled() {
		event.setResult(null);

		assertThatThrownBy(() -> service.settleEvent(event)).isInstanceOf(IllegalStateException.class);
		verify(betRepository, never()).findByEventIdAndStatus(anyLong(), any());
	}

	private static Bet pendingBet(Long id, Long userId, Outcome selection, String stake, String payout) {
		return Bet.builder().id(id).userId(userId).eventId(12L).selection(selection).stake(new BigDecimal(stake))
				.odd(new BigDecimal("2.15")).potentialPayout(new BigDecimal(payout)).status(BetStatus.PENDING)
				.placedAt(NOW.minusSeconds(600)).build();
	}
}
