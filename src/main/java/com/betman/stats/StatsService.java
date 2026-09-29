package com.betman.stats;

import com.betman.bet.BetRepository;
import com.betman.bet.BetStatus;
import com.betman.common.money.Money;
import com.betman.event.EventStatus;
import com.betman.event.SportEventRepository;
import com.betman.stats.dto.StatsSummaryResponse;
import com.betman.user.UserRepository;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatsService {

	private final UserRepository userRepository;
	private final BetRepository betRepository;
	private final SportEventRepository eventRepository;

	public StatsService(UserRepository userRepository, BetRepository betRepository,
			SportEventRepository eventRepository) {
		this.userRepository = userRepository;
		this.betRepository = betRepository;
		this.eventRepository = eventRepository;
	}

	@Transactional(readOnly = true)
	public StatsSummaryResponse summary() {
		Map<BetStatus, Long> bets = new EnumMap<>(BetStatus.class);
		for (BetStatus status : BetStatus.values()) {
			bets.put(status, betRepository.countByStatus(status));
		}
		Map<EventStatus, Long> events = new EnumMap<>(EventStatus.class);
		for (EventStatus status : EventStatus.values()) {
			events.put(status, eventRepository.countByStatus(status));
		}
		return new StatsSummaryResponse(userRepository.count(), bets, Money.round(betRepository.sumStake()),
				Money.round(betRepository.sumPotentialPayoutByStatus(BetStatus.WON)), events);
	}
}
