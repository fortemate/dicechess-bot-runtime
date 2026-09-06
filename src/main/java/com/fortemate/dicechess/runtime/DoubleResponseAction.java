package com.fortemate.dicechess.runtime;

import com.google.gson.annotations.SerializedName;

/**
 * A bot's response to a {@code doubleDecision} delivery.
 *
 * @param acceptDouble {@code true} to accept the pending double offer, {@code false} to decline it
 * @param isResign {@code true} to resign the game
 */
public record DoubleResponseAction(
		boolean acceptDouble,
		@SerializedName("resign") boolean isResign) {

	/**
	 * Creates a double response action.
	 *
	 * @param acceptDouble {@code true} to accept the pending double offer, {@code false} to decline it
	 */
	public DoubleResponseAction(boolean acceptDouble) {
		this(acceptDouble, false);
	}

	/**
	 * Returns an action that accepts the pending double offer.
	 *
	 * @return an accepting action
	 */
	public static DoubleResponseAction accept() {
		return new DoubleResponseAction(true, false);
	}

	/**
	 * Returns an action that explicitly declines the pending double offer.
	 *
	 * @return a declining action
	 */
	public static DoubleResponseAction decline() {
		return new DoubleResponseAction(false, false);
	}

	/**
	 * Synonym for {@link #decline()}: drops the double and forfeits at the current stake.
	 *
	 * @return a declining action
	 */
	public static DoubleResponseAction drop() {
		return decline();
	}

	/**
	 * Returns an action that resigns the game.
	 *
	 * @return a resigning action
	 */
	public static DoubleResponseAction resign() {
		return new DoubleResponseAction(false, true);
	}
}
