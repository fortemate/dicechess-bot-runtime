package com.fortemate.dicechess.runtime;

import java.util.List;
import java.util.Objects;

/**
 * Authenticated context for the turn a bot must play.
 *
 * @param gameId the game's id, suitable for strategy-owned per-game state
 * @param seat the bot's seat, exactly {@code White} or {@code Black}
 * @param version the authoritative game-state version carried by the delivery
 * @param dfen the position plus the rolled dice for the side to move
 * @param clock the game clock, or {@code null} for an untimed game
 * @param legalMoves every complete legal turn, each as its sequence of UCI micro-moves. An empty
 *     list means the server is auto-passing, so no bot action is required; {@code null} means the
 *     moves are unknown because the inline tree was absent/capped and no fallback result was
 *     available
 * @param mayOfferDraw whether the platform currently permits this side to offer a draw; absent,
 *     null, or malformed wire values fail closed to {@code false}
 */
public record TurnContext(
		String gameId,
		String seat,
		long version,
		String dfen,
		GameClock clock,
		List<List<String>> legalMoves,
		boolean mayOfferDraw) {

	/** Creates a validated context with an immutable deep copy of the legal-move paths. */
	public TurnContext {
		requireText(gameId, "gameId");
		requireSeat(seat);
		requireText(dfen, "dfen");
		if (legalMoves != null) {
			legalMoves = legalMoves.stream()
					.map(path -> List.copyOf(Objects.requireNonNull(path, "legal move path must not be null")))
					.toList();
		}
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
