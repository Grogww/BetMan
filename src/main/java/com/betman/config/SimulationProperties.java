package com.betman.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the event lifecycle simulation ({@code betman.simulation.*}).
 */
@Validated
@ConfigurationProperties(prefix = "betman.simulation")
public record SimulationProperties(
		boolean enabled,
		@Min(1) int minScheduledEvents,
		@Min(1) int matchDurationSeconds,
		@Min(1000) long tickIntervalMs) {
}
