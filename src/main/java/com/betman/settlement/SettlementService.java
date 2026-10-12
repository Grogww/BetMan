package com.betman.settlement;

import com.betman.bet.Bet;
import com.betman.bet.BetRepository;
import com.betman.bet.BetStatus;
import com.betman.common.money.Money;
import com.betman.event.Sport;
import com.betman.event.SportEvent;
import com.betman.wallet.WalletService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Settles the pending bets of a finished event: winners become WON and receive their potential
 * payout as a BET_PAYOUT credit; losers become LOST with no wallet movement.
 * Only PENDING bets are touched, so running it twice never pays twice.
 */
@Slf4j
@Service
public class SettlementService {

	static final String BETS_SETTLED = "betman.bets.settled";
	static final String BETS_PAYOUT = "betman.bets.payout";

	private final BetRepository betRepository;
	private final WalletService walletService;
	private final Clock clock;
	private final MeterRegistry meterRegistry;

	public SettlementService(BetRepository betRepository, WalletService walletService, Clock clock, MeterRegistry meterRegistry) {
		this.betRepository = betRepository;
		this.walletService = walletService;
		this.clock = clock;
		this.meterRegistry = meterRegistry;
		// Series start at zero so increase()/rate() in Prometheus also see the first settlement.
		for (Sport sport : Sport.values()) {
			settledCounter(BetStatus.WON, sport);
			settledCounter(BetStatus.LOST, sport);
			payoutSummary(sport);
		}
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
		List<BigDecimal> payouts = new ArrayList<>();

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
				payouts.add(bet.getPotentialPayout());
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
		recordSettlement(event.getSport(), won, lost, payouts);
		return summary;
	}

	/**
	 * Counts the settled bets by result and records each prize paid, only after the transaction
	 * commits, so a rollback does not inflate the metrics. Without a transaction (unit tests) the
	 * metrics are recorded right away.
	 */
	private void recordSettlement(Sport sport, int won, int lost, List<BigDecimal> payouts) {
		Runnable record = () -> {
			settledCounter(BetStatus.WON, sport).increment(won);
			settledCounter(BetStatus.LOST, sport).increment(lost);
			DistributionSummary payout = payoutSummary(sport);
			payouts.forEach(amount -> payout.record(amount.doubleValue()));
		};
		if (TransactionSynchronizationManager.isSynchronizationActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					record.run();
				}
			});
		} else {
			record.run();
		}
	}

	private Counter settledCounter(BetStatus result, Sport sport) {
		return Counter.builder(BETS_SETTLED)
				.description("Apostas liquidadas por resultado")
				.tag("result", result.name())
				.tag("sport", sport.name())
				.register(meterRegistry);
	}

	private DistributionSummary payoutSummary(Sport sport) {
		return DistributionSummary.builder(BETS_PAYOUT)
				.description("Prêmio pago por aposta ganha")
				.baseUnit("BRL")
				.tag("sport", sport.name())
				.register(meterRegistry);
	}
}
