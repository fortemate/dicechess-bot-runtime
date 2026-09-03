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
| `WebhookKeys` | Immutable active and pending secret configuration for zero-downtime rotation. |
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
import com.fortemate.dicechess.runtime.WebhookKeys;
import java.util.List;

// A lambda implements onTurn. The inherited draw callback explicitly declines.
BotStrategy strategy = context -> new TurnAction(List.of("e2e4"));
WebhookKeys keys = WebhookKeys.fromEnvironment();
WebhookHandler handler = new WebhookHandler(keys, strategy);
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

### Webhook key management and zero-downtime rotation (ADR 004)

`dicechess-play-api` implements a two-phase staged webhook management contract (ADR 004). Endpoints
configure secrets via `WebhookKeys`, which supports three immutable operational states:

1. **Pending only (`WebhookKeys.pendingOnly`):** Initial registration of an unverified endpoint.
   The endpoint answers version-2 verification challenges with the candidate secret.
2. **Active and pending (`WebhookKeys.activeAndPending`):** Staged rotation during which
   activation challenges are verified against the pending key only, while ongoing gameplay
   deliveries (`yourTurn`, `drawDecision`, `doubleOpportunity`, `doubleDecision`) are accepted
   under either key.
3. **Active only (`WebhookKeys.activeOnly`):** Steady-state operation after successful cutover.

#### How verification v2 works

During activation, `play-api` sends a signed verification challenge declaring `"version": 2`:

```json
{
  "type": "verification",
  "version": 2,
  "bot": { "team": "acme", "name": "greedy" },
  "setupId": "whs_01K4EXAMPLE",
  "revision": "whrev_01K4SETUP",
  "nonce": "<unpadded base64url carrying >= 128 random bits>"
}
```

The runtime verifies the request signature and timestamp freshness against the **pending key only**.
Upon successful verification, it returns:

```json
{
  "nonce": "<echoed nonce>",
  "proof": "<lowercase hex HMAC-SHA256>"
}
```

The proof is calculated independently over the exact raw request bytes:
`HMAC-SHA256(pendingSecretUtf8, ASCII("dicechess-webhook-activate-v2\n") || rawRequestBodyUtf8)`.
JSON reserialization is never used for either signature verification or proof bytes.

#### Deployment and rotation lifecycle

- **First registration:** Stage the candidate secret as `DICECHESS_WEBHOOK_NEXT_SECRET`
  (`pendingOnly`). Trigger setup creation in `play-api`. When the verification challenge succeeds,
  read back authoritative state via `GET /me/bots/{team}/{name}/webhook` (or `/admin/...`). Once
  confirmed, promote the secret to `DICECHESS_WEBHOOK_SECRET` (`activeOnly`).
- **URL replacement:** Replacing an endpoint URL changes the trust boundary and always issues a fresh
  secret. Stage the candidate secret on the new URL as `pendingOnly` or `activeAndPending`, trigger
  activation, verify authoritative readback, and promote.
- **Zero-downtime same-URL rotation:**
  1. **Stage:** Configure both `DICECHESS_WEBHOOK_SECRET` (current active) and
     `DICECHESS_WEBHOOK_NEXT_SECRET` (new candidate) on the serving bot (`activeAndPending`).
  2. **Verify fleet:** Ensure all serving instances across the fleet are running the dual-key configuration.
  3. **Activate:** Issue the activation request in `play-api`. Any fleet node can verify the challenge
     with the pending key and return the valid proof.
  4. **Authoritative readback:** Call `GET .../webhook` to confirm the new `registrationId` and revision
     commit. If activation failed or the response was ambiguous, the old active key remains usable
     for all deliveries.
  5. **Promote:** Promote `DICECHESS_WEBHOOK_NEXT_SECRET` to `DICECHESS_WEBHOOK_SECRET` and remove
     `DICECHESS_WEBHOOK_NEXT_SECRET` across the fleet.
- **Single-key limitation:** Endpoints configured with only a single secret cannot perform safe
  same-URL session rotation without downtime; upgrading to `WebhookKeys` is required.
- **No automatic promotion:** The runtime never mutates or promotes keys automatically upon
  answering a challenge. Key promotion remains an explicit operator configuration step following
  authoritative server readback.
- **Constant-time dual-key checks:** When both keys are configured, gameplay delivery verification
  evaluates both keys using constant-time comparison without early termination to eliminate timing oracles.
- **Redaction:** Secrets, signatures, response proofs, and raw authenticated payloads are never exposed
  in `toString()`, exceptions, error responses, or logs.

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

Or with dual-key rotation support:

```java
WebhookKeys keys = WebhookKeys.fromEnvironment();
new WebhookHandler(keys, context -> new TurnAction(List.of("e2e4")));
```

Then update context access for `seat`, `version`, `GameClock`, and `mayOfferDraw`, and override
`onDrawDecision` before registering the `draws` capability. A v2 strategy that does not override the
method safely declines draw offers if the capability is enabled accidentally.

One-key endpoints cannot use safe same-URL session rotation without downtime; adopt `WebhookKeys`
with `DICECHESS_WEBHOOK_SECRET` and `DICECHESS_WEBHOOK_NEXT_SECRET` for zero-downtime rotation.
Existing version-1 and version-absent `verification` requests retain the legacy unsigned exact-nonce
echo behavior so bot-token registration remains backward compatible.

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
