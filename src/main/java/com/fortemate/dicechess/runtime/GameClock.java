package com.fortemate.dicechess.runtime;

/**
 * The game clock visible to a bot decision.
 *
 * @param remainingMillis milliseconds left on the bot's own clock
 * @param opponentRemainingMillis milliseconds left on the opponent's clock
 * @param incrementMillis the per-turn Fischer increment in milliseconds, or {@code null} for a
 *     control with no increment
 */
public record GameClock(long remainingMillis, long opponentRemainingMillis, Long incrementMillis) {}
