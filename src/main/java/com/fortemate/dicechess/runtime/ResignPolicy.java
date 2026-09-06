package com.fortemate.dicechess.runtime;

/**
 * Policy governing whether a bot should resign incoming gameplay deliveries.
 *
 * <p>Consulted before strategy dispatch on all delivery types. When {@link #shouldResign()}
 * returns {@code true}, the handler answers with a resigning response without invoking the
 * strategy.
 */
@FunctionalInterface
public interface ResignPolicy {

	/** A policy that never requests resignation. */
	ResignPolicy NEVER = () -> false;

	/**
	 * Returns whether the bot should resign the current delivery.
	 *
	 * @return {@code true} to resign without calling strategy, {@code false} to proceed to strategy
	 */
	boolean shouldResign();
}
