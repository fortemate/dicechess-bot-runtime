package com.fortemate.dicechess.runtime;

/**
 * A bot's response to a {@code drawDecision} delivery.
 *
 * @param acceptDraw {@code true} to accept the pending draw offer, {@code false} to decline it
 */
public record DrawAction(boolean acceptDraw) {

	/**
	 * Returns an action that accepts the pending draw offer.
	 *
	 * @return an accepting action
	 */
	public static DrawAction accept() {
		return new DrawAction(true);
	}

	/**
	 * Returns an action that explicitly declines the pending draw offer.
	 *
	 * @return a declining action
	 */
	public static DrawAction decline() {
		return new DrawAction(false);
	}
}
