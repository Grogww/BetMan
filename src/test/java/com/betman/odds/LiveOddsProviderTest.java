package com.betman.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.betman.common.error.OddsProviderUnavailableException;
import com.betman.config.OddsProviderProperties;
import com.betman.event.EventStatus;
import com.betman.event.Sport;
import com.betman.event.SportEvent;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;
import java.util.Random;
import org.junit.jupiter.api.Test;

class LiveOddsProviderTest {

	private static final Instant NOW = Instant.parse("2026-09-15T12:00:00Z");
	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

	private final SportEvent event = SportEvent.builder().id(12L).sport(Sport.FOOTBALL)
			.homeTeam("Tubarões do Vale").awayTeam("Leões da Serra").startsAt(NOW.plusSeconds(300))
			.status(EventStatus.SCHEDULED).oddHome(new BigDecimal("2.15")).oddDraw(new BigDecimal("3.30"))
			.oddAway(new BigDecimal("3.10")).createdAt(NOW).build();

	@Test
	void returnsOddsWithinFivePercentOfEventOddsAndLatency() {
		OddsProviderProperties props = new OddsProviderProperties(0, 2, 0.0);
		LiveOddsProvider provider = new LiveOddsProvider(props, new Random(1L), CLOCK, registry);

		for (int i = 0; i < 200; i++) {
			LiveOdds live = provider.fetch(event);

			assertThat(live.eventId()).isEqualTo(12L);
			assertThat(live.latencyMs()).isBetween(0L, 2L);
			assertThat(live.fetchedAt()).isEqualTo(NOW);
			assertThat(live.oddHome()).isBetween(new BigDecimal("2.04"), new BigDecimal("2.26"));
			assertThat(live.oddDraw()).isBetween(new BigDecimal("3.13"), new BigDecimal("3.47"));
			assertThat(live.oddAway()).isBetween(new BigDecimal("2.94"), new BigDecimal("3.26"));
		}
	}

	@Test
	void failsWithProviderUnavailableWhenFailureRateIsOne() {
		OddsProviderProperties props = new OddsProviderProperties(0, 0, 1.0);
		LiveOddsProvider provider = new LiveOddsProvider(props, new Random(1L), CLOCK, registry);

		assertThatThrownBy(() -> provider.fetch(event)).isInstanceOf(OddsProviderUnavailableException.class);
	}

	@Test
	void neverFailsWhenFailureRateIsZero() {
		OddsProviderProperties props = new OddsProviderProperties(0, 0, 0.0);
		LiveOddsProvider provider = new LiveOddsProvider(props, new Random(3L), CLOCK, registry);

		for (int i = 0; i < 100; i++) {
			assertThat(provider.fetch(event)).isNotNull();
		}
	}

	@Test
	void successfulCallRecordsLatencyWithSuccessOutcome() {
		LiveOddsProvider provider = new LiveOddsProvider(new OddsProviderProperties(20, 20, 0.0), new Random(1L),
				CLOCK, registry);

		provider.fetch(event);
		provider.fetch(event);

		Timer success = registry.get(LiveOddsProvider.LATENCY).tag("outcome", "success").timer();
		assertThat(success.count()).isEqualTo(2);
		assertThat(success.max(TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(20.0);
		assertThat(registry.find(LiveOddsProvider.LATENCY).tag("outcome", "failure").timer()).isNull();
		assertThat(registry.get(LiveOddsProvider.FAILURES).counter().count()).isZero();
	}

	@Test
	void failedCallCountsFailureAndRecordsLatencyWithFailureOutcome() {
		LiveOddsProvider provider = new LiveOddsProvider(new OddsProviderProperties(0, 0, 1.0), new Random(1L),
				CLOCK, registry);

		for (int i = 0; i < 3; i++) {
			assertThatThrownBy(() -> provider.fetch(event)).isInstanceOf(OddsProviderUnavailableException.class);
		}

		assertThat(registry.get(LiveOddsProvider.FAILURES).counter().count()).isEqualTo(3.0);
		assertThat(registry.get(LiveOddsProvider.LATENCY).tag("outcome", "failure").timer().count()).isEqualTo(3);
		assertThat(registry.find(LiveOddsProvider.LATENCY).tag("outcome", "success").timer()).isNull();
	}

	@Test
	void variedOddsNeverDropBelowMinimum() {
		SportEvent lowOdds = SportEvent.builder().id(1L).oddHome(new BigDecimal("1.01"))
				.oddDraw(new BigDecimal("1.01")).oddAway(new BigDecimal("1.01")).build();
		LiveOddsProvider provider = new LiveOddsProvider(new OddsProviderProperties(0, 0, 0.0), new Random(5L),
				CLOCK, registry);

		for (int i = 0; i < 100; i++) {
			LiveOdds live = provider.fetch(lowOdds);
			assertThat(live.oddHome()).isGreaterThanOrEqualTo(OddsCalculator.MIN_ODD);
			assertThat(live.oddDraw()).isGreaterThanOrEqualTo(OddsCalculator.MIN_ODD);
			assertThat(live.oddAway()).isGreaterThanOrEqualTo(OddsCalculator.MIN_ODD);
		}
	}
}
