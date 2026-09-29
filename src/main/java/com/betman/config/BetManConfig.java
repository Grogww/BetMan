package com.betman.config;

import java.time.Clock;
import java.util.Random;
import java.util.random.RandomGenerator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Core infrastructure beans. {@link Clock} and {@link RandomGenerator} are injected everywhere
 * time or randomness is needed so tests can replace them with deterministic instances.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({ SimulationProperties.class, OddsProviderProperties.class, LimitsProperties.class })
public class BetManConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

	/**
	 * {@link Random} is thread-safe, which matters because the scheduler thread and the
	 * request threads share this single instance.
	 */
	@Bean
	public RandomGenerator randomGenerator() {
		return new Random();
	}
}
