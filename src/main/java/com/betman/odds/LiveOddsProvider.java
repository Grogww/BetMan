package com.betman.odds;

import com.betman.common.error.OddsProviderUnavailableException;
import com.betman.config.OddsProviderProperties;
import com.betman.event.SportEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.random.RandomGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Simulates an unstable third-party odds API: random latency, random failures and odds that
 * drift up to ±5% around the event odds. The result is for display only and is never persisted.
 */
@Slf4j
@Component
public class LiveOddsProvider {

	static final double MAX_VARIATION = 0.05;
	static final String LATENCY = "betman.odds.provider.latency";
	static final String FAILURES = "betman.odds.provider.failures";

	private final OddsProviderProperties properties;
	private final RandomGenerator random;
	private final Clock clock;
	private final MeterRegistry meterRegistry;
	private final Counter failures;

	public LiveOddsProvider(OddsProviderProperties properties, RandomGenerator random, Clock clock,
			MeterRegistry meterRegistry) {
		this.properties = properties;
		this.random = random;
		this.clock = clock;
		this.meterRegistry = meterRegistry;
		this.failures = Counter.builder(FAILURES)
				.description("Chamadas ao provedor de odds que falharam")
				.register(meterRegistry);
	}

	/** Measures every call, tagging the latency with {@code outcome=success|failure}. */
	public LiveOdds fetch(SportEvent event) {
		Timer.Sample sample = Timer.start(meterRegistry);
		try {
			LiveOdds odds = callProvider(event);
			sample.stop(latencyTimer("success"));
			return odds;
		} catch (OddsProviderUnavailableException ex) {
			sample.stop(latencyTimer("failure"));
			failures.increment();
			throw ex;
		}
	}

	private Timer latencyTimer(String outcome) {
		return Timer.builder(LATENCY)
				.description("Latência das chamadas ao provedor de odds")
				.tag("outcome", outcome)
				.publishPercentileHistogram()
				.minimumExpectedValue(Duration.ofMillis(1))
				.maximumExpectedValue(Duration.ofSeconds(5))
				.register(meterRegistry);
	}

	private LiveOdds callProvider(SportEvent event) {
		long latencyMs = properties.latencyMinMs()
				+ random.nextLong(properties.latencyMaxMs() - properties.latencyMinMs() + 1);
		simulateLatency(latencyMs, event.getId());
		log.debug("Odds provider responded eventId={} latencyMs={}", event.getId(), latencyMs);

		if (random.nextDouble() < properties.failureRate()) {
			log.warn("Odds provider failure simulated eventId={} latencyMs={} failureRate={}", event.getId(), latencyMs,
					properties.failureRate());
			throw new OddsProviderUnavailableException(event.getId());
		}

		LiveOdds odds = new LiveOdds(event.getId(), vary(event.getOddHome()), vary(event.getOddDraw()),
				vary(event.getOddAway()), latencyMs, clock.instant());
		log.debug("Live odds fetched eventId={} oddHome={} oddDraw={} oddAway={} latencyMs={}", event.getId(),
				odds.oddHome(), odds.oddDraw(), odds.oddAway(), latencyMs);
		return odds;
	}

	private BigDecimal vary(BigDecimal odd) {
		double factor = 1.0 + (random.nextDouble() * 2 - 1) * MAX_VARIATION;
		BigDecimal varied = odd.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_EVEN);
		return varied.max(OddsCalculator.MIN_ODD);
	}

	private void simulateLatency(long latencyMs, Long eventId) {
		if (latencyMs <= 0) {
			return;
		}
		try {
			Thread.sleep(latencyMs);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			log.warn("Odds provider call interrupted eventId={}", eventId);
			throw new OddsProviderUnavailableException(eventId);
		}
	}
}
