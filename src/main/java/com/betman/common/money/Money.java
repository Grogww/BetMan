package com.betman.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Helpers for monetary values: scale 2, HALF_EVEN rounding and pt-BR formatting.
 */
public final class Money {

	public static final int SCALE = 2;
	public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;

	private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

	private Money() {
	}

	public static BigDecimal round(BigDecimal value) {
		return value.setScale(SCALE, ROUNDING);
	}

	/** Formats as {@code R$ 1.234,56}. */
	public static String format(BigDecimal value) {
		DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(PT_BR));
		return "R$ " + format.format(round(value));
	}
}
