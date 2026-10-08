package com.betman.bet;

import com.betman.bet.dto.BetResponse;
import com.betman.bet.dto.PlaceBetRequest;
import com.betman.common.error.EventNotOpenException;
import com.betman.common.error.InsufficientBalanceException;
import com.betman.common.error.LimitExceededException;
import com.betman.common.error.NotFoundException;
import com.betman.common.money.Money;
import com.betman.common.web.PageResponse;
import com.betman.config.LimitsProperties;
import com.betman.event.EventStatus;
import com.betman.event.Sport;
import com.betman.event.SportEvent;
import com.betman.event.SportEventService;
import com.betman.wallet.WalletService;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
public class BetService {

	static final String BETS_PLACED = "betman.bets.placed";
	static final String BETS_STAKE = "betman.bets.stake";

	private final BetRepository betRepository;
	private final SportEventService eventService;
	private final WalletService walletService;
	private final LimitsProperties limits;
	private final Clock clock;
	private final MeterRegistry meterRegistry;

	public BetService(BetRepository betRepository, SportEventService eventService, WalletService walletService,
			LimitsProperties limits, Clock clock, MeterRegistry meterRegistry) {
		this.betRepository = betRepository;
		this.eventService = eventService;
		this.walletService = walletService;
		this.limits = limits;
		this.clock = clock;
		this.meterRegistry = meterRegistry;
	}

	/**
	 * Registers the bet and debits the stake in one database transaction. The odd is captured
	 * from the event on the server; the client never sends it.
	 */
	@Transactional
	public BetResponse place(Long userId, PlaceBetRequest request) {
		SportEvent event = eventService.getOrThrow(request.eventId());
		if (event.getStatus() != EventStatus.SCHEDULED) {
			throw new EventNotOpenException(event.getId(), event.getStatus().name());
		}

		BigDecimal stake = Money.round(request.stake());
		if (stake.compareTo(limits.minStake()) < 0 || stake.compareTo(limits.maxStake()) > 0) {
			throw new LimitExceededException("O valor da aposta deve estar entre " + Money.format(limits.minStake())
					+ " e " + Money.format(limits.maxStake()) + ".");
		}

		BigDecimal balance = walletService.getBalance(userId);
		if (balance.compareTo(stake) < 0) {
			throw new InsufficientBalanceException(balance, stake, "o valor da aposta");
		}

		BigDecimal odd = event.oddFor(request.selection());
		BigDecimal potentialPayout = Money.round(stake.multiply(odd));

		Bet bet = betRepository.save(Bet.builder()
				.userId(userId)
				.eventId(event.getId())
				.selection(request.selection())
				.stake(stake)
				.odd(odd)
				.potentialPayout(potentialPayout)
				.status(BetStatus.PENDING)
				.placedAt(clock.instant())
				.build());
		BigDecimal balanceAfter = walletService.debitStake(userId, stake, bet.getId());

		log.info("Bet placed betId={} userId={} eventId={} selection={} stake={} odd={} potentialPayout={} balance={}",
				bet.getId(), userId, event.getId(), bet.getSelection(), stake, odd, potentialPayout, balanceAfter);
		recordPlacedBet(event.getSport(), stake);
		return BetResponse.from(bet, event, balanceAfter);
	}

	/**
	 * Counts the bet and its stake only after the transaction commits, so a rollback (e.g. a
	 * concurrent update on the wallet) does not inflate the metrics. Without a transaction
	 * (unit tests) the metrics are recorded right away.
	 */
	private void recordPlacedBet(Sport sport, BigDecimal stake) {
		Runnable record = () -> {
			meterRegistry.counter(BETS_PLACED, "sport", sport.name()).increment();
			DistributionSummary.builder(BETS_STAKE)
					.description("Valor apostado por aposta criada")
					.baseUnit("BRL")
					.tag("sport", sport.name())
					.register(meterRegistry)
					.record(stake.doubleValue());
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

	@Transactional(readOnly = true)
	public PageResponse<BetResponse> list(Long userId, BetStatus status, Pageable pageable) {
		Page<Bet> page = status == null
				? betRepository.findByUserId(userId, pageable)
				: betRepository.findByUserIdAndStatus(userId, status, pageable);
		Map<Long, SportEvent> events = eventsOf(page.getContent());
		return PageResponse.from(page, bet -> BetResponse.from(bet, events.get(bet.getEventId()), null));
	}

	/** A bet that belongs to another user is reported as not found. */
	@Transactional(readOnly = true)
	public BetResponse get(Long userId, Long betId) {
		Bet bet = betRepository.findByIdAndUserId(betId, userId).orElseThrow(() -> NotFoundException.bet(betId));
		return BetResponse.from(bet, eventService.getOrThrow(bet.getEventId()), null);
	}

	private Map<Long, SportEvent> eventsOf(List<Bet> bets) {
		List<Long> ids = bets.stream().map(Bet::getEventId).distinct().toList();
		return eventService.findAllById(ids).stream().collect(Collectors.toMap(SportEvent::getId, Function.identity()));
	}
}
