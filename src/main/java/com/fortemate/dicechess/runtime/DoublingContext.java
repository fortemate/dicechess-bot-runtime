package com.fortemate.dicechess.runtime;

/**
 * Common pre-roll context for stake-doubling decisions in a staked game.
 */
public interface DoublingContext {

	/**
	 * Returns the game's unique identifier.
	 *
	 * @return the game id
	 */
	String gameId();

	/**
	 * Returns the decision actor's seat, exactly {@code White} or {@code Black}.
	 *
	 * @return the seat
	 */
	String seat();

	/**
	 * Returns the authoritative game-state version carried by the delivery.
	 *
	 * @return the version
	 */
	long version();

	/**
	 * Returns the pre-roll position; no dice are present.
	 *
	 * @return the DFEN string
	 */
	String dfen();

	/**
	 * Returns the game clock, or {@code null} for an untimed game.
	 *
	 * @return the clock or {@code null}
	 */
	GameClock clock();

	/**
	 * Returns the complete doubling state.
	 *
	 * @return the doubling state
	 */
	DoublingState doubling();

	/**
	 * Returns the typed doubling decision.
	 *
	 * @return the doubling decision
	 */
	DoublingDecision decision();

	/**
	 * Returns the opaque decision identifier for this doubling episode.
	 *
	 * @return the decision id
	 */
	default String decisionId() {
		return doubling().decision().id();
	}

	/**
	 * Returns the current active stake in whole currency units.
	 *
	 * @return the current stake
	 */
	default long currentStake() {
		return doubling().currentStake();
	}

	/**
	 * Returns the proposed doubled stake in whole currency units.
	 *
	 * @return the proposed stake
	 */
	default long proposedStake() {
		return doubling().decision().proposedStake();
	}

	/**
	 * Returns the initial stake in whole currency units.
	 *
	 * @return the initial stake
	 */
	default long initialStake() {
		return doubling().initialStake();
	}

	/**
	 * Returns the doubling cube multiplier.
	 *
	 * @return the cube value
	 */
	default int cubeValue() {
		return doubling().cubeValue();
	}

	/**
	 * Returns the seat owning the cube, or {@code null} if centered.
	 *
	 * @return the cube owner seat or {@code null}
	 */
	default String cubeOwner() {
		return doubling().cubeOwner();
	}

	/**
	 * Returns the configured maximum multiplier cap.
	 *
	 * @return the maximum multiplier
	 */
	default int maximumMultiplier() {
		return doubling().maximumMultiplier();
	}

	/**
	 * Returns whether the decision actor is eligible to offer a double.
	 *
	 * @return {@code true} if eligible to offer
	 */
	default boolean mayOfferDouble() {
		return doubling().mayOfferDouble();
	}

	/**
	 * Returns the seat whose turn it is.
	 *
	 * @return the turn seat
	 */
	default String turnSeat() {
		return doubling().turnSeat();
	}

	/**
	 * Returns the stake currency name.
	 *
	 * @return the currency name
	 */
	default String currency() {
		return doubling().currency();
	}
}
