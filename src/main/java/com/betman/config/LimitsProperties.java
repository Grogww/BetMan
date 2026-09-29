package com.betman.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Monetary limits applied by the business rules ({@code betman.limits.*}).
 */
@Validated
@ConfigurationProperties(prefix = "betman.limits")
public record LimitsProperties(
		@NotNull @DecimalMin("0.01") BigDecimal minStake,
		@NotNull @DecimalMin("0.01") BigDecimal maxStake,
		@NotNull @DecimalMin("0.01") BigDecimal minDeposit,
		@NotNull @DecimalMin("0.01") BigDecimal maxDeposit,
		@NotNull @DecimalMin("0.01") BigDecimal minWithdraw,
		@NotNull @DecimalMin("0.00") BigDecimal welcomeBonus) {
}
