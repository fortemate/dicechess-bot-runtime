package com.fortemate.dicechess.runtime;

import java.util.Objects;
import java.util.Set;

/**
 * The doubling state of a staked game.
 *
 * <p>A centered cube ({@code cubeValue == 1}) is unowned ({@code cubeOwner == null}); a doubled
 * cube is owned by the seat that last accepted a double.
 *
 * @param currency the stake currency, exactly {@code PLAY_CREDIT}
 * @param initialStake the initial stake in whole currency units
 * @param currentStake the current active stake in whole currency units
 * @param cubeValue the doubling cube multiplier (1, 2, 4, 8, 16, 32, 64)
 * @param cubeOwner the seat owning the cube, or {@code null} if centered
 * @param maximumMultiplier the configured maximum multiplier cap (2, 4, 8, 16, 32, 64)
 * @param mayOfferDouble whether the current decision actor is eligible to offer a double
 * @param turnSeat the seat whose turn it is (survives out-of-turn response phase)
 * @param decision the pending doubling decision, or {@code null} if no decision is pending
 */
public record DoublingState(
		String currency,
		long initialStake,
		long currentStake,
		int cubeValue,
		String cubeOwner,
		int maximumMultiplier,
		boolean mayOfferDouble,
		String turnSeat,
		DoublingDecision decision) {

	/** Permitted doubling cube values. */
	public static final Set<Integer> VALID_CUBE_VALUES = Set.of(1, 2, 4, 8, 16, 32, 64);

	/** Permitted maximum multiplier caps. */
	public static final Set<Integer> VALID_MULTIPLIERS = Set.of(2, 4, 8, 16, 32, 64);

	/** The required currency name for staked games. */
	public static final String CURRENCY_PLAY_CREDIT = "PLAY_CREDIT";

	/**
	 * Creates a validated doubling state.
	 */
	public DoublingState {
		requireText(currency, "currency");
		if (!currency.equals(CURRENCY_PLAY_CREDIT)) {
			throw new IllegalArgumentException("currency must be " + CURRENCY_PLAY_CREDIT);
		}
		requirePositive(initialStake, "initialStake");
		requirePositive(currentStake, "currentStake");
		if (!VALID_CUBE_VALUES.contains(cubeValue)) {
			throw new IllegalArgumentException("cubeValue must be one of " + VALID_CUBE_VALUES);
		}
		if (!VALID_MULTIPLIERS.contains(maximumMultiplier)) {
			throw new IllegalArgumentException("maximumMultiplier must be one of " + VALID_MULTIPLIERS);
		}
		if (cubeValue == 1) {
			if (cubeOwner != null) {
				throw new IllegalArgumentException("centered cube (cubeValue == 1) must have null cubeOwner");
			}
		} else {
			requireSeat(cubeOwner, "cubeOwner");
		}
		requireSeat(turnSeat, "turnSeat");
	}

	private static void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
	}

	private static void requireSeat(String seat, String field) {
		Objects.requireNonNull(seat, field + " must not be null");
		if (!seat.equals("White") && !seat.equals("Black")) {
			throw new IllegalArgumentException(field + " must be White or Black");
		}
	}

	private static void requirePositive(long value, String field) {
		if (value < 1) {
			throw new IllegalArgumentException(field + " must be at least 1");
		}
	}
}
