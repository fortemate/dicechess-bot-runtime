package com.fortemate.dicechess.runtime;

/**
 * Decision-oriented strategy contract for a DiceChess webhook bot.
 *
 * <p>The turn callback is the single required operation, so this remains a functional interface
 * and can be implemented with a lambda. Optional decision kinds use safe default methods; a bot
 * that opts into the {@code draws} webhook capability overrides {@link #onDrawDecision}.
 *
 * <p>A {@link CustomHandlerServer} can invoke one strategy concurrently for different games.
 * Implementations that keep mutable state must therefore provide their own synchronization.
 */
@FunctionalInterface
public interface BotStrategy {

	/**
	 * Chooses the complete turn and whether to offer a draw.
	 *
	 * @param context the authenticated turn context
	 * @return the turn action; never {@code null}
	 */
	TurnAction onTurn(TurnContext context);

	/**
	 * Decides whether to accept an opponent's pending draw offer.
	 *
	 * <p>The default explicitly declines, so adding the {@code draws} capability without an
	 * override never accepts a draw accidentally.
	 *
	 * @param context the authenticated, pre-roll draw-decision context
	 * @return the draw action; never {@code null}
	 */
	default DrawAction onDrawDecision(DrawDecisionContext context) {
		return DrawAction.decline();
	}
}
