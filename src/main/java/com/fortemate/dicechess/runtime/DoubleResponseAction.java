package com.fortemate.dicechess.runtime;

/**
 * A bot's response to a {@code doubleDecision} delivery.
 *
 * @param acceptDouble {@code true} to accept the pending double offer, {@code false} to decline it
 */
public record DoubleResponseAction(boolean acceptDouble) {

	/**
	 * Returns an action that accepts the pending double offer.
	 *
	 * @return an accepting action
	 */
	public static DoubleResponseAction accept() {
		return new DoubleResponseAction(true);
	}

	/**
	 * Returns an action that explicitly declines the pending double offer.
	 *
	 * @return a declining action
	 */
	public static DoubleResponseAction decline() {
		return new DoubleResponseAction(false);
	}

	/**
	 * Synonym for {@link #decline()}: drops the double and forfeits at the current stake.
	 *
	 * @return a declining action
	 */
	public static DoubleResponseAction drop() {
		return decline();
	}
}
