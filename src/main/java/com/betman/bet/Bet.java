package com.betman.bet;

import com.betman.event.Outcome;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Bet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "event_id", nullable = false)
	private Long eventId;

	@Enumerated(EnumType.STRING)
	@Column(name = "selection", nullable = false, length = 10)
	private Outcome selection;

	@Column(name = "stake", nullable = false, precision = 12, scale = 2)
	private BigDecimal stake;

	/** Odd captured from the event at the moment the bet was placed. */
	@Column(name = "odd", nullable = false, precision = 6, scale = 2)
	private BigDecimal odd;

	@Column(name = "potential_payout", nullable = false, precision = 12, scale = 2)
	private BigDecimal potentialPayout;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 10)
	private BetStatus status;

	@Column(name = "placed_at", nullable = false)
	private Instant placedAt;

	@Column(name = "settled_at")
	private Instant settledAt;
}
