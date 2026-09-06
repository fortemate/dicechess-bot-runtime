package com.fortemate.dicechess.runtime;

import com.google.gson.annotations.SerializedName;

/**
 * A bot's response to a {@code doubleOpportunity} delivery.
 *
 * @param offerDouble {@code true} to offer a stake double, {@code false} to proceed to roll
 * @param isResign {@code true} to resign the game
 */
public record DoubleOfferAction(
		boolean offerDouble,
		@SerializedName("resign") boolean isResign) {

	/**
	 * Creates a double offer action.
	 *
	 * @param offerDouble {@code true} to offer a stake double, {@code false} to proceed to roll
	 */
	public DoubleOfferAction(boolean offerDouble) {
		this(offerDouble, false);
	}

	/**
	 * Returns an action that offers a stake double.
	 *
	 * @return an offering action
	 */
	public static DoubleOfferAction offer() {
		return new DoubleOfferAction(true, false);
	}

	/**
	 * Returns an action that declines to double and proceeds to roll.
	 *
	 * @return a rolling action
	 */
	public static DoubleOfferAction roll() {
		return new DoubleOfferAction(false, false);
	}

	/**
	 * Synonym for {@link #roll()}: declines to offer a double and proceeds to roll.
	 *
	 * @return a rolling action
	 */
	public static DoubleOfferAction decline() {
		return roll();
	}

	/**
	 * Returns an action that resigns the game.
	 *
	 * @return a resigning action
	 */
	public static DoubleOfferAction resign() {
		return new DoubleOfferAction(false, true);
	}
}
