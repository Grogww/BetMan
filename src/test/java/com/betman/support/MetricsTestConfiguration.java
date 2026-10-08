package com.betman.support;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * {@code @WebMvcTest} slices do not auto-configure metrics, but {@code GlobalExceptionHandler}
 * needs a {@link MeterRegistry}. Import this to provide an in-memory one.
 */
@TestConfiguration(proxyBeanMethods = false)
public class MetricsTestConfiguration {

	@Bean
	public MeterRegistry meterRegistry() {
		return new SimpleMeterRegistry();
	}
}
