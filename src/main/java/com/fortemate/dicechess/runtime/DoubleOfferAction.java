package com.fortemate.dicechess.runtime;

/**
 * A bot's response to a {@code doubleOpportunity} delivery.
 *
 * @param offerDouble {@code true} to offer a stake double, {@code false} to proceed to roll
 */
public record DoubleOfferAction(boolean offerDouble) {

	/**
	 * Returns an action that offers a stake double.
	 *
	 * @return an offering action
	 */
	public static DoubleOfferAction offer() {
		return new DoubleOfferAction(true);
	}

	/**
	 * Returns an action that declines to double and proceeds to roll.
	 *
	 * @return a rolling action
	 */
	public static DoubleOfferAction roll() {
		return new DoubleOfferAction(false);
	}

	/**
	 * Synonym for {@link #roll()}: declines to offer a double and proceeds to roll.
	 *
	 * @return a rolling action
	 */
	public static DoubleOfferAction decline() {
		return roll();
	}
}
