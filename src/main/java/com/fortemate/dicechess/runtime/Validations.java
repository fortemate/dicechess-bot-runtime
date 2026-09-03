package com.fortemate.dicechess.runtime;

import java.util.Objects;

final class Validations {

	private Validations() {}

	static void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
	}

	static void requireSeat(String seat) {
		requireSeat(seat, "seat");
	}

	static void requireSeat(String seat, String field) {
		Objects.requireNonNull(seat, field + " must not be null");
		if (!seat.equals("White") && !seat.equals("Black")) {
			throw new IllegalArgumentException(field + " must be White or Black");
		}
	}

	static void requirePositive(long value, String field) {
		if (value < 1) {
			throw new IllegalArgumentException(field + " must be at least 1");
		}
	}

	static void requireNoDice(String dfen) {
		var parts = dfen.trim().split("\\s+");
		if (parts.length > 6) {
			throw new IllegalArgumentException("dfen must not contain dice tokens before roll");
		}
	}
}
