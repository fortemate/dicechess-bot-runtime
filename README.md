# Dice Chess Bot Runtime

[![CI](https://github.com/fortemate/dicechess-bot-runtime/actions/workflows/ci.yaml/badge.svg)](https://github.com/fortemate/dicechess-bot-runtime/actions/workflows/ci.yaml)
[![Javadoc](https://img.shields.io/badge/Javadoc-fortemate-1E90FF)](https://fortemate.github.io/dicechess-bot-runtime/)
[![Maven Central](https://img.shields.io/maven-central/v/com.fortemate/dicechess-bot-runtime)](https://central.sonatype.com/artifact/com.fortemate/dicechess-bot-runtime)
[![Release](https://img.shields.io/github/v/tag/fortemate/dicechess-bot-runtime?label=release&sort=semver)](https://github.com/fortemate/dicechess-bot-runtime/releases)
![Java](https://img.shields.io/badge/Java-25-orange)
[![License: MIT](https://img.shields.io/badge/License-MIT-lightgrey)](./LICENSE)

The transport and protocol plumbing shared by Dice Chess webhook bots: HMAC-SHA256 signature
verification, the one-time ownership handshake, and (optionally) an HTTP server for the Azure
Functions custom-handler model. A bot author supplies one thing — a function from a
[`TurnContext`](#usage) to a list of UCI moves — and gets a working webhook bot.

Every public type speaks only `String`, `Long`, `java.util.List`, and `java.util.Map`. Nothing
library-specific crosses the boundary, so this is callable identically from Java, Kotlin, or
Scala — see [`dicechess-bot-gcp-onnx`](https://github.com/fortemate/dicechess-bot-gcp-onnx) for an
engine-linked consumer. It reads the position from `ctx.dfen()` and uses only the context its
strategy needs.

## Layout

| Path | Role |
| --- | --- |
| `Signatures` | HMAC-SHA256 sign/verify, ±5 minute replay window, constant-time comparison. |
| `WebhookHandler` | Orchestrates one delivery: handshake, signature check, dispatch to the strategy function. Never throws. |
| `TurnContext` | What the strategy function sees: `gameId`, `dfen`, the game `clock` (both sides' remaining time plus the per-turn Fischer increment, all in ms — the whole `clock` is `null` for an untimed game), and every complete legal turn already walked out (`null` if unknown). |
| `CustomHandlerServer` | A JDK `HttpServer` wrapper reading `FUNCTIONS_CUSTOMHANDLER_PORT` — optional; bring your own HTTP layer if you'd rather. |
| `JsonFiles` | Generic JSON-object-of-strings file loader (an opening book, or any similar lookup table), degrades gracefully when the file is absent. |

## Usage

```java
import com.fortemate.dicechess.runtime.CustomHandlerServer;
import com.fortemate.dicechess.runtime.TurnContext;
import com.fortemate.dicechess.runtime.WebhookHandler;
import java.util.List;
import java.util.function.Function;

Function<TurnContext, List<String>> strategy = ctx -> List.of("e2e4"); // your move logic
String secret = System.getenv("DICECHESS_WEBHOOK_SECRET");
WebhookHandler handler = new WebhookHandler(secret, strategy);
CustomHandlerServer.startFromEnvironment(handler);
```

A strategy with no engine of its own can skip `dfen` entirely and just pick one path from
`ctx.legalMoves()` — pass play-api's base URL to the other `WebhookHandler` constructor and the
rare capped turn (the tree too large to inline) is fetched from the public
`GET /games/{id}/moves` automatically:

```java
WebhookHandler handler = new WebhookHandler(secret, "https://play-api.fortemate.com", strategy);
```

Full API docs with more examples: <https://fortemate.github.io/dicechess-bot-runtime/>.

## Dependency

Published to Maven Central as `com.fortemate:dicechess-bot-runtime` — no extra repository
configuration needed. Replace `VERSION` below with whatever the badge above currently shows.

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
