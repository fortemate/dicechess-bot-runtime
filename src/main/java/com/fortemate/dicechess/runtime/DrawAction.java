package com.fortemate.dicechess.runtime;

import com.google.gson.annotations.SerializedName;

/**
 * A bot's response to a {@code drawDecision} delivery.
 *
 * @param acceptDraw {@code true} to accept the pending draw offer, {@code false} to decline it
 * @param isResign {@code true} to resign the game
 */
public record DrawAction(
		boolean acceptDraw,
		@SerializedName("resign") boolean isResign) {

	/**
	 * Creates a draw action.
	 *
	 * @param acceptDraw {@code true} to accept the pending draw offer, {@code false} to decline it
	 */
	public DrawAction(boolean acceptDraw) {
		this(acceptDraw, false);
	}

	/**
	 * Returns an action that accepts the pending draw offer.
	 *
	 * @return an accepting action
	 */
	public static DrawAction accept() {
		return new DrawAction(true, false);
	}

	/**
	 * Returns an action that explicitly declines the pending draw offer.
	 *
	 * @return a declining action
	 */
	public static DrawAction decline() {
		return new DrawAction(false, false);
	}

	/**
	 * Returns an action that resigns the game.
	 *
	 * @return a resigning action
	 */
	public static DrawAction resign() {
		return new DrawAction(false, true);
	}
}
