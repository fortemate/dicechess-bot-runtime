# Dice Chess Bot Runtime

[![CI](https://github.com/fortemate/dicechess-bot-runtime/actions/workflows/ci.yaml/badge.svg)](https://github.com/fortemate/dicechess-bot-runtime/actions/workflows/ci.yaml)
[![Javadoc](https://img.shields.io/badge/Javadoc-fortemate-1E90FF)](https://fortemate.github.io/dicechess-bot-runtime/)
[![Maven Central](https://img.shields.io/maven-central/v/com.fortemate/dicechess-bot-runtime)](https://central.sonatype.com/artifact/com.fortemate/dicechess-bot-runtime)
[![Release](https://img.shields.io/github/v/tag/fortemate/dicechess-bot-runtime?label=release&sort=semver)](https://github.com/fortemate/dicechess-bot-runtime/releases)
![Java](https://img.shields.io/badge/Java-25-orange)
[![License: MIT](https://img.shields.io/badge/License-MIT-lightgrey)](./LICENSE)

The transport and protocol plumbing shared by Dice Chess webhook bots: HMAC-SHA256 signature
verification, the one-time ownership handshake, typed turn and draw decisions, and (optionally) an
HTTP server for the Azure Functions custom-handler model. A bot author supplies one
[`BotStrategy`](#usage) and gets a working webhook bot.

Only JDK types cross the public boundary; Gson remains an implementation detail. The API is usable
from Java, Kotlin, and Scala — see
[`dicechess-bot-gcp-onnx`](https://github.com/fortemate/dicechess-bot-gcp-onnx) for an engine-linked
consumer. A strategy can read only the context it needs, whether that is the opaque DFEN, the
server-provided legal turns, or the game clock.

## Layout

| Path | Role |
| --- | --- |
| `Signatures` | HMAC-SHA256 sign/verify, ±5 minute replay window, constant-time comparison. |
| `BotStrategy` | One required `onTurn` callback plus safe default methods for optional decisions. |
| `TurnContext` / `TurnAction` | The signed turn input and the exact `moves` / `offerDraw` response. |
| `DrawDecisionContext` / `DrawAction` | A dice-free draw decision and its exact `acceptDraw` response. |
| `DoubleOpportunityContext` / `DoubleOfferAction` | A dice-free double opportunity and its exact `decisionId` / `offerDouble` response. |
| `DoubleDecisionContext` / `DoubleResponseAction` | A dice-free double response decision and its exact `decisionId` / `acceptDouble` response. |
| `DoublingState` / `DoublingDecision` | Public stake and cube state, multipliers, and typed doubling decision representation. |
| `GameClock` | The mover's and opponent's remaining milliseconds, plus a nullable Fischer increment. |
| `WebhookHandler` | Orchestrates ownership verification, signature checks, typed parsing, and strategy dispatch with bounded request errors. |
| `CustomHandlerServer` | A JDK `HttpServer` wrapper reading `FUNCTIONS_CUSTOMHANDLER_PORT` — optional; bring your own HTTP layer if you'd rather. |
| `JsonFiles` | Generic JSON-object-of-strings file loader (an opening book, or any similar lookup table), degrades gracefully when the file is absent. |

## Usage

```java
import com.fortemate.dicechess.runtime.BotStrategy;
import com.fortemate.dicechess.runtime.CustomHandlerServer;
import com.fortemate.dicechess.runtime.TurnAction;
import com.fortemate.dicechess.runtime.WebhookHandler;
import java.util.List;

// A lambda implements onTurn. The inherited draw callback explicitly declines.
BotStrategy strategy = context -> new TurnAction(List.of("e2e4"));
String secret = System.getenv("DICECHESS_WEBHOOK_SECRET");
WebhookHandler handler = new WebhookHandler(secret, strategy);
CustomHandlerServer.startFromEnvironment(handler);
```

`BotStrategy` stays a functional interface because only `onTurn` is abstract. Override
`onDrawDecision` only when the webhook is registered with the `draws` capability and has an
intentional draw policy:

```java
import com.fortemate.dicechess.runtime.DrawAction;
import com.fortemate.dicechess.runtime.DrawDecisionContext;
import com.fortemate.dicechess.runtime.TurnContext;

BotStrategy strategy = new BotStrategy() {
    @Override
    public TurnAction onTurn(TurnContext context) {
        return new TurnAction(List.of("e2e4")); // offerDraw defaults to false
    }

    @Override
    public DrawAction onDrawDecision(DrawDecisionContext context) {
        return DrawAction.decline(); // accept only after an evaluated bot policy says so
    }
};
```

The capability controls delivery, not the Java type: without the exact lowercase `draws`
registration capability, play-api automatically declines an opponent's offer before revealing the
dice and sends the normal `yourTurn` delivery. With it, play-api first sends a dice-free
`drawDecision`. The default `onDrawDecision` returns `DrawAction.decline()`, so adopting v2 never
silently opts a bot into accepting draws. Offering a draw is a turn action and defaults to false;
check `TurnContext.mayOfferDraw()` before requesting one.

### Stake doubling decisions

The exact lowercase `doubling` webhook capability opts an endpoint into pre-roll stake doubling
decisions in staked games. Staked games use closed-loop `PLAY_CREDIT` units and follow the accepted
`play-api` contract (ADR-0019). The platform resolves any pending draw first. If no draw is pending
and the turn owner is eligible to offer, play-api delivers a dice-free `doubleOpportunity` before
rolling. If an offer is made, the responder receives a dice-free `doubleDecision` before the roll
point.

A bot author overrides `onDoubleOpportunity` and/or `onDoubleDecision` to integrate engine policy:

```java
import com.fortemate.dicechess.runtime.DoubleDecisionContext;
import com.fortemate.dicechess.runtime.DoubleOfferAction;
import com.fortemate.dicechess.runtime.DoubleOpportunityContext;
import com.fortemate.dicechess.runtime.DoubleResponseAction;

BotStrategy strategy = new BotStrategy() {
    @Override
    public TurnAction onTurn(TurnContext context) {
        return new TurnAction(List.of("e2e4"));
    }

    @Override
    public DoubleOfferAction onDoubleOpportunity(DoubleOpportunityContext context) {
        // Evaluate engine offer threshold using context.currentStake(), context.cubeValue(), etc.
        return DoubleOfferAction.roll(); // default safely rolls without offering
    }

    @Override
    public DoubleResponseAction onDoubleDecision(DoubleDecisionContext context) {
        // Evaluate engine take/drop threshold using context.proposedStake(), context.offeredBy(), etc.
        return DoubleResponseAction.decline(); // default safely declines the offer
    }
};
```

The runtime defaults are intentionally safe: `onDoubleOpportunity` defaults to `DoubleOfferAction.roll()`,
and `onDoubleDecision` defaults to `DoubleResponseAction.decline()`. Enabling the capability without an
override never offers or accepts a double accidentally. Both contexts are structurally dice-free;
`yourTurn` begins only after the authoritative roll.

### Turn context

`TurnContext` exposes the game id, the bot's seat, the monotonic state version, the opaque DFEN,
the game clock, the flattened legal turns, and `mayOfferDraw`:

- `clock` is `null` for an untimed game. Otherwise its remaining values are milliseconds from the
  bot's point of view; `incrementMillis` is non-null only for a Fischer control.
- `legalMoves` is a list of complete root-to-leaf UCI paths. An empty list means the server is
  auto-passing, so no bot action is required; `null` means the server did not inline the tree or the
  fallback fetch failed.
- `mayOfferDraw` is fail-closed: an absent, null, or malformed optional wire field becomes `false`.

A strategy with no engine of its own can pick one path directly from `legalMoves`. Pass play-api's
base URL to the other `WebhookHandler` constructor and a capped inline tree is fetched from the
public `GET /games/{id}/moves` endpoint automatically:

```java
WebhookHandler handler = new WebhookHandler(secret, "https://play-api.fortemate.com", strategy);
```

`DrawDecisionContext` deliberately contains no legal moves or dice-dependent data. It carries only
the common game id, seat, state version, pre-roll DFEN, and clock needed to evaluate the offer.

### Thread safety

One `BotStrategy` instance is shared by its `WebhookHandler`. `CustomHandlerServer` uses virtual
threads, so callbacks for different games can overlap. Keep the strategy stateless where possible;
otherwise synchronize per-game state or use thread-safe collections. Contexts and actions are
immutable snapshots, but any engine, cache, or search state captured by the strategy remains the
consumer's concurrency responsibility.

## Migrating from v1

Version 2 is an intentional source-breaking redesign. The v1
`Function<TurnContext, List<String>>` callback and the old `TurnContext` shape are removed; there is
no compatibility constructor or adapter in the v2 artifact. Existing immutable v1 artifacts remain
available for consumers that have not migrated.

The minimal source change wraps the move list in a typed action:

```java
// v1
new WebhookHandler(secret, context -> List.of("e2e4"));

// v2
new WebhookHandler(secret, context -> new TurnAction(List.of("e2e4")));
```

Then update context access for `seat`, `version`, `GameClock`, and `mayOfferDraw`, and override
`onDrawDecision` before registering the `draws` capability. A v2 strategy that does not override the
method safely declines draw offers if the capability is enabled accidentally.

Full API docs with more examples: <https://fortemate.github.io/dicechess-bot-runtime/>.

## Dependency

Published to Maven Central as `com.fortemate:dicechess-bot-runtime` — no extra repository
configuration needed. Replace `VERSION` below with whatever the badge above currently shows.

The repository currently develops the breaking line as `2.0.0-SNAPSHOT`. That snapshot version is
development metadata, not evidence that v2 has been published; releases remain an explicit
human-owned operation.

Maven:

```xml
<dependency>
	<groupId>com.fortemate</groupId>
	<artifactId>dicechess-bot-runtime</artifactId>
	<version>VERSION</version>
</dependency>
```

sbt (plain `%`, not `%%` — this is a Java artifact, not cross-built per Scala version):

```scala
libraryDependencies += "com.fortemate" % "dicechess-bot-runtime" % "VERSION"
```

Requires a JRE 25 or newer — the jar's bytecode targets class file version 69 and an older JVM
will refuse to load it (`Unsupported class file version`).

The public Java package and JPMS automatic module name are both
`com.fortemate.dicechess.runtime`.

## Local development

Requires JDK 25, Maven, and [mise](https://mise.jdx.dev/) for the repository-standard task aliases
and Git hooks.

```bash
mise install
mise run setup
mise run check
```

`mise run check` executes the complete test suite, packages the library, and treats malformed or
incomplete public Javadoc as a build failure.

## Why no framework

The webhook contract is one HTTP request in, one HTTP response out, on a hard clock — a
dependency-heavy web framework buys nothing here and would work against the "callable from
anywhere" goal. The only third-party dependency is [Gson](https://github.com/google/gson), used
internally to parse/build the small, fixed envelope shapes; it never appears in a public method
signature.
