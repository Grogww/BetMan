package com.betman.bet;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BetRepository extends JpaRepository<Bet, Long> {

	Page<Bet> findByUserId(Long userId, Pageable pageable);

	Page<Bet> findByUserIdAndStatus(Long userId, BetStatus status, Pageable pageable);

	Optional<Bet> findByIdAndUserId(Long id, Long userId);

	List<Bet> findByEventIdAndStatus(Long eventId, BetStatus status);

	long countByStatus(BetStatus status);

	@Query("select coalesce(sum(b.stake), 0) from Bet b")
	BigDecimal sumStake();

	@Query("select coalesce(sum(b.potentialPayout), 0) from Bet b where b.status = :status")
	BigDecimal sumPotentialPayoutByStatus(@Param("status") BetStatus status);
}
