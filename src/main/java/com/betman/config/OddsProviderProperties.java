package com.betman.config;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the simulated external odds provider ({@code betman.odds-provider.*}).
 */
@Validated
@ConfigurationProperties(prefix = "betman.odds-provider")
public record OddsProviderProperties(
		@Min(0) long latencyMinMs,
		@Min(0) long latencyMaxMs,
		@DecimalMin("0.0") @DecimalMax("1.0") double failureRate) {

	public OddsProviderProperties {
		if (latencyMaxMs < latencyMinMs) {
			throw new IllegalArgumentException("betman.odds-provider.latency-max-ms must be >= latency-min-ms");
		}
	}
}
