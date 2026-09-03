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
		DoublingState doubling) implements DoublingContext {

	/**
	 * Creates a validated double-decision context.
	 */
	public DoubleDecisionContext {
		Validations.requireText(gameId, "gameId");
		Validations.requireSeat(seat);
		Validations.requireText(dfen, "dfen");
		Validations.requireNoDice(dfen);
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

	@Override
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
}
