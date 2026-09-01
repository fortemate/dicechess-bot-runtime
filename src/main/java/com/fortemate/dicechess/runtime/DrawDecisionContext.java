package com.fortemate.dicechess.runtime;

import java.util.Objects;

/**
 * Authenticated pre-roll context for deciding an opponent's pending draw offer.
 *
 * <p>This context deliberately has no legal moves or dice-dependent data: the platform requests
 * a draw decision before revealing the roll.
 *
 * @param gameId the game's id
 * @param seat the bot's seat, exactly {@code White} or {@code Black}
 * @param version the authoritative game-state version carried by the delivery
 * @param dfen the pre-roll position; no dice are present
 * @param clock the game clock, or {@code null} for an untimed game
 */
public record DrawDecisionContext(String gameId, String seat, long version, String dfen, GameClock clock) {

	/** Creates a validated draw-decision context. */
	public DrawDecisionContext {
		requireText(gameId, "gameId");
		requireSeat(seat);
		requireText(dfen, "dfen");
	}

	private static void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
	}

	private static void requireSeat(String seat) {
		Objects.requireNonNull(seat, "seat must not be null");
		if (!seat.equals("White") && !seat.equals("Black")) {
			throw new IllegalArgumentException("seat must be White or Black");
		}
	}
}
