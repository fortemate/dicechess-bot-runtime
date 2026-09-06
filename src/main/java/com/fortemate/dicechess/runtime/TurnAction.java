package com.fortemate.dicechess.runtime;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Objects;

/**
 * A bot's response to a {@code yourTurn} delivery.
 *
 * @param moves the complete sequence of UCI micro-moves; an empty list plays nothing and is not a
 *     forced-pass command
 * @param offerDraw whether to offer a draw together with the completed turn
 * @param isResign whether to resign the game
 */
public record TurnAction(
		List<String> moves,
		boolean offerDraw,
		@SerializedName("resign") boolean isResign) {

	/**
	 * Creates an immutable turn action.
	 *
	 * @throws NullPointerException if {@code moves} or one of its elements is {@code null}
	 */
	public TurnAction {
		Objects.requireNonNull(moves, "moves must not be null");
		moves = List.copyOf(moves);
	}

	/**
	 * Creates a turn action that does not offer a draw and does not resign.
	 *
	 * @param moves the complete sequence of UCI micro-moves
	 */
	public TurnAction(List<String> moves) {
		this(moves, false, false);
	}

	/**
	 * Creates a turn action that does not resign.
	 *
	 * @param moves the complete sequence of UCI micro-moves
	 * @param offerDraw whether to offer a draw together with the completed turn
	 */
	public TurnAction(List<String> moves, boolean offerDraw) {
		this(moves, offerDraw, false);
	}

	/**
	 * Returns a turn action that resigns the game.
	 *
	 * @return a resigning turn action
	 */
	public static TurnAction resign() {
		return new TurnAction(List.of(), false, true);
	}
}
