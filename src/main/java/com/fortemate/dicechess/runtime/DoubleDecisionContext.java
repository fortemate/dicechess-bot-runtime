package com.fortemate.dicechess.runtime;

import java.util.Objects;

/**
 * Authenticated pre-roll context for deciding an opponent's pending stake double offer.
 *
 * <p>This context is structurally dice-free: the platform resolves the double offer before
 * revealing the roll. Note that {@link #turnSeat()} identifies the opponent whose turn will resume
 * after an acceptance, whereas {@link #seat()} identifies the responder currently deciding.
 *
 * @param gameId the game's id
 * @param seat the responder's seat, exactly {@code White} or {@code Black}
 * @param version the authoritative game-state version carried by the delivery
 * @param dfen the pre-roll position; no dice are present
 * @param clock the game clock, or {@code null} for an untimed game
 * @param doubling the complete doubling state and pending response decision
 */
public record DoubleDecisionContext(
		String gameId,
		String seat,
		long version,
		String dfen,
		GameClock clock,
		DoublingState doubling) {

	/**
	 * Creates a validated double-decision context.
	 */
	public DoubleDecisionContext {
		requireText(gameId, "gameId");
		requireSeat(seat);
		requireText(dfen, "dfen");
		requireNoDice(dfen);
		Objects.requireNonNull(doubling, "doubling must not be null");
		if (!(doubling.decision() instanceof DoublingDecision.Response response)) {
			throw new IllegalArgumentException("doubling decision must be a Response");
		}
		if (!response.seat().equals(seat)) {
			throw new IllegalArgumentException("decision seat must match context seat");
		}
		if (!doubling.turnSeat().equals(response.offeredBy())) {
			throw new IllegalArgumentException("turnSeat must match offeredBy");
		}
		if (doubling.mayOfferDouble()) {
			throw new IllegalArgumentException("mayOfferDouble must be false for response");
		}
	}

	/**
	 * Returns the opaque decision identifier for this doubling episode.
	 *
	 * @return the decision id
	 */
	public String decisionId() {
		return doubling.decision().id();
	}

	/**
	 * Returns the typed response decision.
	 *
	 * @return the response decision
	 */
	public DoublingDecision.Response decision() {
		return (DoublingDecision.Response) doubling.decision();
	}

	/**
	 * Returns the seat that offered the stake double.
	 *
	 * @return the offerer's seat
	 */
	public String offeredBy() {
		return decision().offeredBy();
	}

	/**
	 * Returns the current active stake in whole currency units (the amount lost if declined).
	 *
	 * @return the current stake
	 */
	public long currentStake() {
		return doubling.currentStake();
	}

	/**
	 * Returns the proposed doubled stake in whole currency units (the stake if accepted).
	 *
	 * @return the proposed stake
	 */
	public long proposedStake() {
		return doubling.decision().proposedStake();
	}

	/**
	 * Returns the initial stake in whole currency units.
	 *
	 * @return the initial stake
	 */
	public long initialStake() {
		return doubling.initialStake();
	}

	/**
	 * Returns the doubling cube multiplier.
	 *
	 * @return the cube value
	 */
	public int cubeValue() {
		return doubling.cubeValue();
	}

	/**
	 * Returns the seat owning the cube, or {@code null} if centered.
	 *
	 * @return the cube owner seat or {@code null}
	 */
	public String cubeOwner() {
		return doubling.cubeOwner();
	}

	/**
	 * Returns the configured maximum multiplier cap.
	 *
	 * @return the maximum multiplier
	 */
	public int maximumMultiplier() {
		return doubling.maximumMultiplier();
	}

	/**
	 * Returns whether this bot is eligible to offer a double (always {@code false} for response).
	 *
	 * @return {@code false}
	 */
	public boolean mayOfferDouble() {
		return doubling.mayOfferDouble();
	}

	/**
	 * Returns the seat whose turn it was when the double was offered.
	 *
	 * @return the turn seat
	 */
	public String turnSeat() {
		return doubling.turnSeat();
	}

	/**
	 * Returns the stake currency name.
	 *
	 * @return the currency name
	 */
	public String currency() {
		return doubling.currency();
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

	private static void requireNoDice(String dfen) {
		var parts = dfen.trim().split("\\s+");
		if (parts.length > 6) {
			throw new IllegalArgumentException("dfen must not contain dice tokens before roll");
		}
	}
}
