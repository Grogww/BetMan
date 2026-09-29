package com.betman.settlement;

import com.betman.bet.Bet;
import com.betman.bet.BetRepository;
import com.betman.bet.BetStatus;
import com.betman.common.money.Money;
import com.betman.event.SportEvent;
import com.betman.wallet.WalletService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settles the pending bets of a finished event: winners become WON and receive their potential
 * payout as a BET_PAYOUT credit; losers become LOST with no wallet movement.
 * Only PENDING bets are touched, so running it twice never pays twice.
 */
@Slf4j
@Service
public class SettlementService {

	private final BetRepository betRepository;
	private final WalletService walletService;
	private final Clock clock;

	public SettlementService(BetRepository betRepository, WalletService walletService, Clock clock) {
		this.betRepository = betRepository;
		this.walletService = walletService;
		this.clock = clock;
	}

	@Transactional
	public SettlementSummary settleEvent(SportEvent event) {
		if (event.getResult() == null) {
			throw new IllegalStateException("Event " + event.getId() + " has no result to settle against");
		}
		List<Bet> pending = betRepository.findByEventIdAndStatus(event.getId(), BetStatus.PENDING);
		Instant now = clock.instant();
		int won = 0;
		int lost = 0;
		BigDecimal paid = Money.round(BigDecimal.ZERO);

		for (Bet bet : pending) {
			if (bet.getStatus() != BetStatus.PENDING) {
				continue; // defensive idempotency guard
			}
			if (bet.getSelection() == event.getResult()) {
				bet.setStatus(BetStatus.WON);
				bet.setSettledAt(now);
				betRepository.save(bet);
				walletService.creditPayout(bet.getUserId(), bet.getPotentialPayout(), bet.getId());
				paid = paid.add(bet.getPotentialPayout());
				won++;
				log.debug("Bet won betId={} userId={} eventId={} payout={}", bet.getId(), bet.getUserId(),
						event.getId(), bet.getPotentialPayout());
			} else {
				bet.setStatus(BetStatus.LOST);
				bet.setSettledAt(now);
				betRepository.save(bet);
				lost++;
				log.debug("Bet lost betId={} userId={} eventId={} stake={}", bet.getId(), bet.getUserId(),
						event.getId(), bet.getStake());
			}
		}

		SettlementSummary summary = new SettlementSummary(won, lost, paid);
		log.info("Settlement completed eventId={} result={} won={} lost={} paid={}", event.getId(),
				event.getResult(), won, lost, paid);
		return summary;
	}
}
