package com.fortemate.dicechess.runtime;

import java.util.Objects;

/**
 * Authenticated pre-roll context for deciding whether to offer a stake double on this bot's turn.
 *
 * <p>This context is structurally dice-free: the platform presents the doubling opportunity before
 * deriving or revealing any dice.
 *
 * @param gameId the game's id
 * @param seat the bot's seat, exactly {@code White} or {@code Black}
 * @param version the authoritative game-state version carried by the delivery
 * @param dfen the pre-roll position; no dice are present
 * @param clock the game clock, or {@code null} for an untimed game
 * @param doubling the complete doubling state and pending offer decision
 */
public record DoubleOpportunityContext(
		String gameId,
		String seat,
		long version,
		String dfen,
		GameClock clock,
		DoublingState doubling) {

	/**
	 * Creates a validated double-opportunity context.
	 */
	public DoubleOpportunityContext {
		requireText(gameId, "gameId");
		requireSeat(seat);
		requireText(dfen, "dfen");
		requireNoDice(dfen);
		Objects.requireNonNull(doubling, "doubling must not be null");
		if (!(doubling.decision() instanceof DoublingDecision.Offer offer)) {
			throw new IllegalArgumentException("doubling decision must be an Offer");
		}
		if (!offer.seat().equals(seat)) {
			throw new IllegalArgumentException("decision seat must match context seat");
		}
		if (!doubling.turnSeat().equals(seat)) {
			throw new IllegalArgumentException("turnSeat must match context seat");
		}
		if (!doubling.mayOfferDouble()) {
			throw new IllegalArgumentException("mayOfferDouble must be true for opportunity");
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
	 * Returns the typed offer decision.
	 *
	 * @return the offer decision
	 */
	public DoublingDecision.Offer decision() {
		return (DoublingDecision.Offer) doubling.decision();
	}

	/**
	 * Returns the current active stake in whole currency units.
	 *
	 * @return the current stake
	 */
	public long currentStake() {
		return doubling.currentStake();
	}

	/**
	 * Returns the proposed doubled stake in whole currency units.
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
	 * Returns whether this bot is eligible to offer a double (always {@code true} for opportunity).
	 *
	 * @return {@code true}
	 */
	public boolean mayOfferDouble() {
		return doubling.mayOfferDouble();
	}

	/**
	 * Returns the seat whose turn it is.
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
