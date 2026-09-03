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
		DoublingState doubling) implements DoublingContext {

	/**
	 * Creates a validated double-opportunity context.
	 */
	public DoubleOpportunityContext {
		Validations.requireText(gameId, "gameId");
		Validations.requireSeat(seat);
		Validations.requireText(dfen, "dfen");
		Validations.requireNoDice(dfen);
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

	@Override
	public DoublingDecision.Offer decision() {
		return (DoublingDecision.Offer) doubling.decision();
	}
}
