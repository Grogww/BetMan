package com.betman.event;

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
@Table(name = "sport_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SportEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(name = "sport", nullable = false, length = 20)
	private Sport sport;

	@Column(name = "home_team", nullable = false, length = 60)
	private String homeTeam;

	@Column(name = "away_team", nullable = false, length = 60)
	private String awayTeam;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private EventStatus status;

	@Column(name = "odd_home", nullable = false, precision = 6, scale = 2)
	private BigDecimal oddHome;

	@Column(name = "odd_draw", nullable = false, precision = 6, scale = 2)
	private BigDecimal oddDraw;

	@Column(name = "odd_away", nullable = false, precision = 6, scale = 2)
	private BigDecimal oddAway;

	@Enumerated(EnumType.STRING)
	@Column(name = "result", length = 10)
	private Outcome result;

	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public BigDecimal oddFor(Outcome outcome) {
		return switch (outcome) {
			case HOME -> oddHome;
			case DRAW -> oddDraw;
			case AWAY -> oddAway;
		};
	}
}
