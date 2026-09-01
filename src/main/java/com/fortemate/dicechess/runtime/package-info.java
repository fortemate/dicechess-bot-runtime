/**
 * Transport runtime for DiceChess webhook bots.
 *
 * <p>A DiceChess bot is a small HTTP endpoint: the platform ({@code play-api}) delivers each turn or
 * optional decision as a signed HTTP request, and the bot answers with a typed action. Everything
 * in this package is the plumbing around that contract — HMAC-SHA256 signature verification ({@link
 * com.fortemate.dicechess.runtime.Signatures}), the one-time ownership handshake and delivery
 * orchestration ({@link com.fortemate.dicechess.runtime.WebhookHandler}), and (optionally) the HTTP
 * server itself ({@link com.fortemate.dicechess.runtime.CustomHandlerServer}) — so a bot author
 * supplies one {@link com.fortemate.dicechess.runtime.BotStrategy}.
 *
 * <p>The whole wiring for a bot's {@code main} method:
 *
 * {@snippet lang="java" :
 * BotStrategy strategy = context -> new TurnAction(List.of("e2e4"));
 * String secret = System.getenv("DICECHESS_WEBHOOK_SECRET");
 * WebhookHandler handler = new WebhookHandler(secret, strategy);
 * CustomHandlerServer.startFromEnvironment(handler);
 * }
 *
 * <p>Only JDK types cross the public boundary — no Gson type, and nothing library-specific, appears
 * in a public signature. {@link com.fortemate.dicechess.runtime.BotStrategy} is a functional
 * interface: {@link com.fortemate.dicechess.runtime.BotStrategy#onTurn(TurnContext)} is its single
 * abstract method, while {@link
 * com.fortemate.dicechess.runtime.BotStrategy#onDrawDecision(DrawDecisionContext)} safely declines
 * by default. Java, Kotlin, and Scala consumers can therefore continue to use a lambda when they
 * need only normal turns.
 *
 * <h2>Draw decisions</h2>
 *
 * <p>The exact lowercase {@code draws} webhook capability opts an endpoint into pre-roll {@code
 * drawDecision} deliveries. Without that capability, play-api declines the offer on the bot's
 * behalf, reveals the dice, and sends a normal {@code yourTurn}. With it, the strategy receives a
 * dice-free {@link com.fortemate.dicechess.runtime.DrawDecisionContext} and returns a {@link
 * com.fortemate.dicechess.runtime.DrawAction}. The default callback returns {@link
 * com.fortemate.dicechess.runtime.DrawAction#decline()}, so enabling runtime v2 never silently
 * accepts a draw.
 *
 * {@snippet lang="java" :
 * BotStrategy strategy = new BotStrategy() {
 *     @Override
 *     public TurnAction onTurn(TurnContext context) {
 *         return new TurnAction(List.of("e2e4")); // offerDraw defaults to false
 *     }
 *
 *     @Override
 *     public DrawAction onDrawDecision(DrawDecisionContext context) {
 *         return DrawAction.decline(); // accept only after an evaluated bot policy says so
 *     }
 * };
 * }
 *
 * <h2>Bots with no engine of their own</h2>
 *
 * <p>{@link com.fortemate.dicechess.runtime.TurnContext#legalMoves} carries every complete legal
 * turn, already walked from the server's prefix tree — a strategy can pick straight from that
 * list and never parse a DFEN or generate a move itself. An empty list means the server is
 * auto-passing, so no bot action is required; {@code null} means the tree is unknown. The rare turn
 * where the tree is too large to inline falls back to {@code GET
 * /games/{id}/moves} — a public, unauthenticated endpoint — if the {@link
 * com.fortemate.dicechess.runtime.WebhookHandler} constructor that takes play-api's base URL was
 * used; otherwise {@code legalMoves} is simply {@code null} on that turn, same as it always is
 * when a strategy doesn't need it.
 *
 * <p>{@link com.fortemate.dicechess.runtime.TurnContext#clock} is {@code null} for an untimed game.
 * Otherwise the {@link com.fortemate.dicechess.runtime.GameClock} values are milliseconds from the
 * bot's point of view; only a Fischer control has a non-null increment. {@link
 * com.fortemate.dicechess.runtime.TurnContext#mayOfferDraw} fails closed to {@code false} when the
 * optional wire field is absent, null, or malformed.
 *
 * <h2>Concurrency</h2>
 *
 * <p>One strategy instance is shared by its handler. {@link
 * com.fortemate.dicechess.runtime.CustomHandlerServer} uses virtual threads, so callbacks for
 * different games may overlap. Contexts and actions are immutable snapshots; mutable engine,
 * cache, or per-game state captured by a strategy must be synchronized or otherwise thread-safe.
 *
 * <h2>Migration from v1</h2>
 *
 * <p>Version 2 intentionally removes the v1 {@code Function<TurnContext, List<String>>} callback
 * and redesigns {@link com.fortemate.dicechess.runtime.TurnContext}. There is no compatibility
 * constructor or callback adapter in the v2 artifact: return a {@link
 * com.fortemate.dicechess.runtime.TurnAction} from {@code onTurn}, update context access, and
 * override {@code onDrawDecision} only when adopting the {@code draws} capability. Previously
 * published immutable v1 artifacts remain unchanged.
 *
 * <h2>What is deliberately not here</h2>
 *
 * <p>DFEN parsing and independent move legality are still not this package's concern — it
 * relays the server's own tree rather than recomputing one, so an engine-linked bot is free to
 * ignore {@code legalMoves} entirely and keep deriving moves from {@code dfen} itself. It also does
 * not read or write an opening book itself; {@link
 * com.fortemate.dicechess.runtime.JsonFiles} is a generic string-map loader a strategy can use for
 * that, or for any similarly simple lookup table.
 */
package com.fortemate.dicechess.runtime;
