package com.fortemate.dicechess.runtime;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An authoritative stake-doubling decision awaiting bot action.
 */
public sealed interface DoublingDecision permits DoublingDecision.Offer, DoublingDecision.Response {

	/** Regular expression pattern for a valid decision id. */
	Pattern ID_PATTERN = Pattern.compile("^double_[A-Za-z0-9_-]+$");

	/**
	 * Returns the opaque decision identifier spanning the doubling episode.
	 *
	 * @return the decision id
	 */
	String id();

	/**
	 * Returns the decision kind, exactly {@code offer} or {@code response}.
	 *
	 * @return the decision kind
	 */
	String kind();

	/**
	 * Returns the seat required to answer this decision, exactly {@code White} or {@code Black}.
	 *
	 * @return the decision seat
	 */
	String seat();

	/**
	 * Returns the proposed doubled stake in whole PLAY_CREDIT units.
	 *
	 * @return the proposed stake
	 */
	long proposedStake();

	/**
	 * An offer decision awaiting the turn owner's choice to offer a double or roll.
	 *
	 * @param id the decision id
	 * @param seat the turn owner's seat, exactly {@code White} or {@code Black}
	 * @param proposedStake the proposed stake if doubled
	 */
	record Offer(String id, String seat, long proposedStake) implements DoublingDecision {

		/** Constant decision kind for an offer opportunity. */
		public static final String KIND = "offer";

		/**
		 * Creates a validated offer decision.
		 */
		public Offer {
			requireDecisionId(id);
			requireSeat(seat);
			requirePositiveStake(proposedStake, "proposedStake");
		}

		@Override
		public String kind() {
			return KIND;
		}
	}

	/**
	 * A response decision awaiting the responder's choice to accept or decline an offer.
	 *
	 * @param id the decision id
	 * @param seat the responder's seat, exactly {@code White} or {@code Black}
	 * @param offeredBy the seat that made the double offer, exactly {@code White} or {@code Black}
	 * @param proposedStake the proposed stake if accepted
	 */
	record Response(String id, String seat, String offeredBy, long proposedStake) implements DoublingDecision {

		/** Constant decision kind for an offer response. */
		public static final String KIND = "response";

		/**
		 * Creates a validated response decision.
		 */
		public Response {
			requireDecisionId(id);
			requireSeat(seat);
			requireSeat(offeredBy);
			requirePositiveStake(proposedStake, "proposedStake");
			if (seat.equals(offeredBy)) {
				throw new IllegalArgumentException("seat and offeredBy must be opposite seats");
			}
		}

		@Override
		public String kind() {
			return KIND;
		}
	}

	private static void requireDecisionId(String id) {
		if (id == null || !ID_PATTERN.matcher(id).matches()) {
			throw new IllegalArgumentException("id must match pattern ^double_[A-Za-z0-9_-]+$");
		}
	}

	private static void requireSeat(String seat) {
		Objects.requireNonNull(seat, "seat must not be null");
		if (!seat.equals("White") && !seat.equals("Black")) {
			throw new IllegalArgumentException("seat must be White or Black");
		}
	}

	private static void requirePositiveStake(long stake, String field) {
		if (stake < 1) {
			throw new IllegalArgumentException(field + " must be at least 1");
		}
	}
}
