package com.fortemate.dicechess.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WebhookHandlerTest {

	private static final String SECRET = "test-webhook-secret";
	private static final long NOW = 1752750000L;
	private static final String TURN_DFEN =
			"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 NBK";
	private static final String DRAW_DFEN =
			"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1";

	// Payload shape and DFEN forms pinned from play-api origin/main@2eac1d3.
	private static final String YOUR_TURN_FIXTURE = """
			{"type":"yourTurn","gameId":"game-uuid","seat":"White","state":{"version":4,"dfen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 NBK","activeSeat":"White","dicePending":true,"status":{"Active":{}},"timeControl":{"Fischer":{"initialSeconds":300,"incrementSeconds":3}},"clocks":{"white":295000,"black":300000},"commit":"c0ffee","seed":null,"clientSeeds":null,"legalMoves":{"e2e4":{}},"players":null,"rated":true,"drawOffer":null,"mayOfferDraw":true}}
			""".strip();

	private static final String DRAW_DECISION_FIXTURE = """
			{"type":"drawDecision","gameId":"game-uuid","seat":"Black","state":{"version":3,"dfen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1","activeSeat":"Black","dicePending":false,"status":{"Active":{}},"timeControl":{"Fischer":{"initialSeconds":300,"incrementSeconds":3}},"clocks":{"white":300000,"black":298000},"commit":"c0ffee","seed":null,"clientSeeds":null,"legalMoves":null,"players":null,"rated":true,"drawOffer":{"pending":true},"mayOfferDraw":null}}
			""".strip();

	private static final String DOUBLE_OPPORTUNITY_FIXTURE = """
			{"type":"doubleOpportunity","gameId":"3f0e2b4c-9a17-4d6e-8b52-1c7d9e0f4a68","seat":"White","state":{"version":0,"dfen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","activeSeat":"White","dicePending":false,"status":{"Active":{}},"timeControl":{"Fischer":{"initialSeconds":300,"incrementSeconds":3}},"clocks":{"white":300000,"black":300000},"commit":"5f64c3ed69253a39c08dd52f34a30e79f347a92fcdfb6f101c2f8db6393dcdfe","seed":null,"clientSeeds":null,"legalMoves":null,"players":{"white":{"kind":"Human","name":null,"rating":null},"black":{"kind":"Bot","name":"acme aggressive","rating":null}},"rated":false,"drawOffer":null,"mayOfferDraw":null,"doubling":{"currency":"PLAY_CREDIT","initialStake":10,"currentStake":10,"cubeValue":1,"cubeOwner":null,"maximumMultiplier":64,"mayOfferDouble":true,"turnSeat":"White","decision":{"id":"double_01K4F4Y7M8R2","kind":"offer","seat":"White","proposedStake":20}}}}
			""".strip();

	private static final String DOUBLE_DECISION_FIXTURE = """
			{"type":"doubleDecision","gameId":"3f0e2b4c-9a17-4d6e-8b52-1c7d9e0f4a68","seat":"Black","state":{"version":1,"dfen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","activeSeat":"Black","dicePending":false,"status":{"Active":{}},"timeControl":{"Fischer":{"initialSeconds":300,"incrementSeconds":3}},"clocks":{"white":295000,"black":300000},"commit":"5f64c3ed69253a39c08dd52f34a30e79f347a92fcdfb6f101c2f8db6393dcdfe","seed":null,"clientSeeds":null,"legalMoves":null,"players":{"white":{"kind":"Human","name":null,"rating":null},"black":{"kind":"Bot","name":"acme aggressive","rating":null}},"rated":false,"drawOffer":null,"mayOfferDraw":null,"doubling":{"currency":"PLAY_CREDIT","initialStake":10,"currentStake":10,"cubeValue":1,"cubeOwner":null,"maximumMultiplier":64,"mayOfferDouble":false,"turnSeat":"White","decision":{"id":"double_01K4F4Y7M8R2","kind":"response","seat":"Black","offeredBy":"White","proposedStake":20}}}}
			""".strip();

	private static final String MINIMAL_OPPORTUNITY = """
			{"type":"doubleOpportunity","gameId":"g1","seat":"White","state":{"version":0,"dfen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","activeSeat":"White","dicePending":false,"doubling":{"currency":"PLAY_CREDIT","initialStake":10,"currentStake":10,"cubeValue":1,"cubeOwner":null,"maximumMultiplier":64,"mayOfferDouble":true,"turnSeat":"White","decision":{"id":"double_01K4F4Y7M8R2","kind":"offer","seat":"White","proposedStake":20}}}}
			""".strip();

	private static final String MINIMAL_DOUBLE_DECISION = """
			{"type":"doubleDecision","gameId":"g1","seat":"Black","state":{"version":1,"dfen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","activeSeat":"Black","dicePending":false,"doubling":{"currency":"PLAY_CREDIT","initialStake":10,"currentStake":10,"cubeValue":1,"cubeOwner":null,"maximumMultiplier":64,"mayOfferDouble":false,"turnSeat":"White","decision":{"id":"double_01K4F4Y7M8R2","kind":"response","seat":"Black","offeredBy":"White","proposedStake":20}}}}
			""".strip();

	private static final String MINIMAL_TURN =
			"{\"type\":\"yourTurn\",\"gameId\":\"g1\",\"seat\":\"White\",\"state\":{\"version\":1,\"dfen\":\"x\",\"activeSeat\":\"White\",\"dicePending\":true}}";

	private static final String MINIMAL_DRAW =
			"{\"type\":\"drawDecision\",\"gameId\":\"g1\",\"seat\":\"Black\",\"state\":{\"version\":2,\"dfen\":\"pre-roll\",\"activeSeat\":\"Black\",\"dicePending\":false,\"drawOffer\":{\"pending\":true}}}";

	private static Map<String, String> signedHeaders(String body, long timestamp) {
		return Map.of(
				WebhookHandler.TIMESTAMP_HEADER, String.valueOf(timestamp),
				WebhookHandler.SIGNATURE_HEADER, Signatures.sign(SECRET, timestamp, body));
	}

	private static WebhookHandler passiveHandler() {
		return new WebhookHandler(SECRET, context -> new TurnAction(List.of()));
	}

	@Test
	void legacyHandshakeEchoesTheNonceWithoutASignature() {
		var response = passiveHandler().handle(Map.of(), "{\"type\":\"verification\",\"nonce\":\"abc123\"}", NOW);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.jsonBody()).isEqualTo("{\"nonce\":\"abc123\"}");
	}

	@Test
	void playApiTurnFixtureMapsEveryV2FieldAndSerializesTheExactAction() {
		var seen = new AtomicReference<TurnContext>();
		var handler = new WebhookHandler(SECRET, context -> {
			seen.set(context);
			return new TurnAction(List.of("e2e4"), true);
		});

		var response = handler.handle(signedHeaders(YOUR_TURN_FIXTURE, NOW), YOUR_TURN_FIXTURE, NOW);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.jsonBody()).isEqualTo("{\"moves\":[\"e2e4\"],\"offerDraw\":true}");
		assertThat(seen.get())
				.extracting(
						TurnContext::gameId,
						TurnContext::seat,
						TurnContext::version,
						TurnContext::dfen,
						TurnContext::mayOfferDraw)
					.containsExactly(
							"game-uuid",
							"White",
							4L,
							TURN_DFEN,
							true);
		assertThat(seen.get().clock()).isEqualTo(new GameClock(295000, 300000, 3000L));
		assertThat(seen.get().legalMoves()).containsExactly(List.of("e2e4"));
	}

	@Test
	void playApiDrawFixtureDispatchesTheDrawCallbackAndSerializesOnlyAcceptDraw() {
		var seen = new AtomicReference<DrawDecisionContext>();
		BotStrategy strategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				return new TurnAction(List.of());
			}

			@Override
			public DrawAction onDrawDecision(DrawDecisionContext context) {
				seen.set(context);
				return DrawAction.accept();
			}
		};
		var handler = new WebhookHandler(SECRET, strategy);

		var response = handler.handle(signedHeaders(DRAW_DECISION_FIXTURE, NOW), DRAW_DECISION_FIXTURE, NOW);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.jsonBody()).isEqualTo("{\"acceptDraw\":true}");
		assertThat(seen.get())
				.extracting(
						DrawDecisionContext::gameId,
						DrawDecisionContext::seat,
						DrawDecisionContext::version,
						DrawDecisionContext::dfen)
					.containsExactly("game-uuid", "Black", 3L, DRAW_DFEN);
		assertThat(seen.get().clock()).isEqualTo(new GameClock(298000, 300000, 3000L));
	}

	@Test
	void defaultDrawDecisionExplicitlyDeclines() {
		var response = passiveHandler().handle(signedHeaders(MINIMAL_DRAW, NOW), MINIMAL_DRAW, NOW);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.jsonBody()).isEqualTo("{\"acceptDraw\":false}");
	}

	@Test
	void missingAndMalformedOptionalTurnFieldsDegradeSafely() {
		var seen = new AtomicReference<TurnContext>();
		var handler = new WebhookHandler(SECRET, context -> {
			seen.set(context);
			return new TurnAction(List.of());
		});

		var minimalResponse = handler.handle(signedHeaders(MINIMAL_TURN, NOW), MINIMAL_TURN, NOW);
		assertThat(minimalResponse.status()).isEqualTo(200);
		assertThat(minimalResponse.jsonBody()).isEqualTo("{\"moves\":[],\"offerDraw\":false}");
		assertThat(seen.get().clock()).isNull();
		assertThat(seen.get().legalMoves()).isNull();
		assertThat(seen.get().mayOfferDraw()).isFalse();

		var nullMayOffer = MINIMAL_TURN.replace("}}", ",\"mayOfferDraw\":null}}");
		assertThat(handler.handle(signedHeaders(nullMayOffer, NOW), nullMayOffer, NOW).status()).isEqualTo(200);
		assertThat(seen.get().mayOfferDraw()).isFalse();

		var malformedMayOffer = MINIMAL_TURN.replace("}}", ",\"mayOfferDraw\":\"yes\"}}");
		assertThat(handler.handle(signedHeaders(malformedMayOffer, NOW), malformedMayOffer, NOW).status())
				.isEqualTo(200);
		assertThat(seen.get().mayOfferDraw()).isFalse();
	}

	@Test
	void clocksAreOrientedToTheBotsSeatAndNonFischerIncrementIsAbsent() {
		var seen = new AtomicReference<DrawDecisionContext>();
		BotStrategy strategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				return new TurnAction(List.of());
			}

			@Override
			public DrawAction onDrawDecision(DrawDecisionContext context) {
				seen.set(context);
				return DrawAction.decline();
			}
		};
		var body = MINIMAL_DRAW.replace(
				"\"drawOffer\"",
				"\"clocks\":{\"white\":60000,\"black\":59000},\"timeControl\":{\"SuddenDeath\":{\"initialSeconds\":60}},\"drawOffer\"");

		var response = new WebhookHandler(SECRET, strategy).handle(signedHeaders(body, NOW), body, NOW);

		assertThat(response.status()).isEqualTo(200);
		assertThat(seen.get().clock()).isEqualTo(new GameClock(59000, 60000, null));
	}

	@Test
	void aMalformedFischerIncrementDegradesToNoIncrement() {
		var seen = new AtomicReference<TurnContext>();
		var handler = new WebhookHandler(SECRET, context -> {
			seen.set(context);
			return new TurnAction(List.of());
		});
		var body = MINIMAL_TURN.replace(
				"}}",
				",\"clocks\":{\"white\":300000,\"black\":300000},\"timeControl\":{\"Fischer\":{\"incrementSeconds\":\"soon\"}}}}");

		var response = handler.handle(signedHeaders(body, NOW), body, NOW);

		assertThat(response.status()).isEqualTo(200);
		assertThat(seen.get().clock()).isEqualTo(new GameClock(300000, 300000, null));
	}

	@Test
	void anInlineMoveTreeIsFlattenedAndAForcedPassStaysAnEmptyList() {
		var seen = new AtomicReference<TurnContext>();
		var handler = new WebhookHandler(SECRET, context -> {
			seen.set(context);
			return new TurnAction(List.of());
		});
		var treeBody = MINIMAL_TURN.replace(
				"}}", ",\"legalMoves\":{\"e2e4\":{\"g1f3\":{},\"b1c3\":{}},\"d2d4\":{\"d4d5\":{}}}}}");

		assertThat(handler.handle(signedHeaders(treeBody, NOW), treeBody, NOW).status()).isEqualTo(200);
		assertThat(seen.get().legalMoves())
				.containsExactlyInAnyOrder(List.of("e2e4", "g1f3"), List.of("e2e4", "b1c3"), List.of("d2d4", "d4d5"));

		var passBody = MINIMAL_TURN.replace("}}", ",\"legalMoves\":{}}}");
		assertThat(handler.handle(signedHeaders(passBody, NOW), passBody, NOW).status()).isEqualTo(200);
		assertThat(seen.get().legalMoves()).isEmpty();
	}

	@Test
	void aCappedMoveTreeUsesThePublicFallback() throws IOException {
		var fallback = "{\"version\":1,\"dfen\":\"x\",\"dicePending\":true,\"legalMoves\":{\"e2e4\":{\"g1f3\":{}}}}";
		var server = stubMovesEndpoint("g1", fallback, new AtomicInteger());
		try {
			var seen = new AtomicReference<TurnContext>();
			var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
			var handler = new WebhookHandler(SECRET, baseUrl, context -> {
				seen.set(context);
				return new TurnAction(List.of());
			});
			var body = MINIMAL_TURN.replace("}}", ",\"legalMoves\":null}}");

			assertThat(handler.handle(signedHeaders(body, NOW), body, NOW).status()).isEqualTo(200);
			assertThat(seen.get().legalMoves()).containsExactly(List.of("e2e4", "g1f3"));
		} finally {
			server.stop(0);
		}
	}

	@Test
	void fallbackMovesMustMatchTheSignedVersionDfenAndPendingRoll() throws IOException {
		var mismatches = List.of(
				"{\"version\":2,\"dfen\":\"x\",\"dicePending\":true,\"legalMoves\":{\"e2e4\":{}}}",
				"{\"version\":1,\"dfen\":\"other\",\"dicePending\":true,\"legalMoves\":{\"e2e4\":{}}}",
				"{\"version\":1,\"dfen\":\"x\",\"dicePending\":false,\"legalMoves\":{\"e2e4\":{}}}");
		var body = MINIMAL_TURN.replace("}}", ",\"legalMoves\":null}}");

		for (var fallback : mismatches) {
			var server = stubMovesEndpoint("g1", fallback, new AtomicInteger());
			try {
				var seen = new AtomicReference<TurnContext>();
				var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
				var handler = new WebhookHandler(SECRET, baseUrl, context -> {
					seen.set(context);
					return new TurnAction(List.of());
				});

				assertThat(handler.handle(signedHeaders(body, NOW), body, NOW).status()).isEqualTo(200);
				assertThat(seen.get().legalMoves()).as(fallback).isNull();
			} finally {
				server.stop(0);
			}
		}
	}

	@Test
	void aFailedFallbackDegradesToUnknownMoves() {
		var seen = new AtomicReference<TurnContext>();
		var handler = new WebhookHandler(SECRET, "http://127.0.0.1:1", context -> {
			seen.set(context);
			return new TurnAction(List.of());
		});
		var body = MINIMAL_TURN.replace("}}", ",\"legalMoves\":null}}");

		assertThat(handler.handle(signedHeaders(body, NOW), body, NOW).status()).isEqualTo(200);
		assertThat(seen.get().legalMoves()).isNull();
	}

	@Test
	void drawDecisionsNeverFetchDiceDependentLegalMoves() throws IOException {
		var calls = new AtomicInteger();
		var server = stubMovesEndpoint("g1", "{\"legalMoves\":{}}", calls);
		try {
			var baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
			var response = new WebhookHandler(SECRET, baseUrl, context -> new TurnAction(List.of()))
					.handle(signedHeaders(MINIMAL_DRAW, NOW), MINIMAL_DRAW, NOW);

			assertThat(response.status()).isEqualTo(200);
			assertThat(calls).hasValue(0);
		} finally {
			server.stop(0);
		}
	}

	@Test
	void signatureAuthenticationPrecedesDecisionParsingAndCallbackDispatch() {
		var calls = new AtomicInteger();
		var handler = new WebhookHandler(SECRET, context -> {
			calls.incrementAndGet();
			return new TurnAction(List.of());
		});
		var malformed = "{\"type\":\"yourTurn\",\"state\":[";
		var badHeaders = Map.of(
				WebhookHandler.TIMESTAMP_HEADER,
				String.valueOf(NOW),
				WebhookHandler.SIGNATURE_HEADER,
				"deadbeef");

		assertThat(handler.handle(badHeaders, malformed, NOW).status()).isEqualTo(401);
		assertThat(handler.handle(signedHeaders(malformed, NOW), malformed, NOW).status()).isEqualTo(400);
		assertThat(calls).hasValue(0);
	}

	@Test
	void aNoncanonicalTypeAfterStateIsRejectedWithoutTraversingDecisionState() {
		var typeLast = "{\"state\":[,\"type\":\"yourTurn\"}";

		assertThat(passiveHandler().handle(Map.of(), typeLast, NOW).status()).isEqualTo(400);
		assertThat(passiveHandler().handle(signedHeaders(typeLast, NOW), typeLast, NOW).status()).isEqualTo(400);
	}

	@Test
	void malformedHeaderMapsReturnABoundedAuthenticationFailure() {
		var nullKeyHeaders = new HashMap<String, String>();
		nullKeyHeaders.put(null, "attacker-controlled-value");

		assertThat(passiveHandler().handle(null, MINIMAL_TURN, NOW).status()).isEqualTo(401);
		assertThat(passiveHandler().handle(nullKeyHeaders, MINIMAL_TURN, NOW).status()).isEqualTo(401);
	}

	@Test
	void badMissingAndStaleSignaturesNeverInvokeEitherCallback() {
		var turnCalls = new AtomicInteger();
		var drawCalls = new AtomicInteger();
		BotStrategy strategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				turnCalls.incrementAndGet();
				return new TurnAction(List.of());
			}

			@Override
			public DrawAction onDrawDecision(DrawDecisionContext context) {
				drawCalls.incrementAndGet();
				return DrawAction.decline();
			}
		};
		var handler = new WebhookHandler(SECRET, strategy);
		var stale = NOW - 3600;

		assertThat(handler.handle(Map.of(), MINIMAL_TURN, NOW).status()).isEqualTo(401);
		assertThat(handler.handle(signedHeaders(MINIMAL_TURN, stale), MINIMAL_TURN, NOW).status()).isEqualTo(401);
		assertThat(handler.handle(Map.of(), MINIMAL_DRAW, NOW).status()).isEqualTo(401);
		assertThat(turnCalls).hasValue(0);
		assertThat(drawCalls).hasValue(0);
	}

	@Test
	void malformedRequiredTurnFieldsAndWrongDecisionStateAreDeterministic400s() {
		var calls = new AtomicInteger();
		var handler = new WebhookHandler(SECRET, context -> {
			calls.incrementAndGet();
			return new TurnAction(List.of());
		});
		var invalidBodies = List.of(
				"{\"type\":\"yourTurn\",\"seat\":\"White\",\"state\":{}}",
				MINIMAL_TURN.replace("\"White\",\"state\"", "\"Red\",\"state\""),
				MINIMAL_TURN.replace("\"version\":1", "\"version\":\"1\""),
				MINIMAL_TURN.replace("\"version\":1", "\"version\":1.5"),
				MINIMAL_TURN.replace("\"dfen\":\"x\"", "\"dfen\":7"),
				MINIMAL_TURN.replace("\"activeSeat\":\"White\"", "\"activeSeat\":\"Black\""),
				MINIMAL_TURN.replace("\"dicePending\":true", "\"dicePending\":false"),
				MINIMAL_TURN.replace("}}", ",\"legalMoves\":[]}}"),
				MINIMAL_TURN.replace("}}", ",\"clocks\":{\"white\":1000}}}"));

		for (var body : invalidBodies) {
			assertThat(handler.handle(signedHeaders(body, NOW), body, NOW).status()).as(body).isEqualTo(400);
		}
		assertThat(calls).hasValue(0);
	}

	@Test
	void malformedDrawStateNeverInvokesTheDrawCallback() {
		var calls = new AtomicInteger();
		BotStrategy strategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				return new TurnAction(List.of());
			}

			@Override
			public DrawAction onDrawDecision(DrawDecisionContext context) {
				calls.incrementAndGet();
				return DrawAction.accept();
			}
		};
		var handler = new WebhookHandler(SECRET, strategy);
		var invalidBodies = List.of(
				MINIMAL_DRAW.replace("\"dicePending\":false", "\"dicePending\":true"),
				MINIMAL_DRAW.replace("\"activeSeat\":\"Black\"", "\"activeSeat\":\"White\""),
				MINIMAL_DRAW.replace(",\"drawOffer\":{\"pending\":true}", ""),
				MINIMAL_DRAW.replace("\"pending\":true", "\"pending\":false"));

		for (var body : invalidBodies) {
			assertThat(handler.handle(signedHeaders(body, NOW), body, NOW).status()).as(body).isEqualTo(400);
		}
		assertThat(calls).hasValue(0);
	}

	@Test
	void headerLookupIsCaseInsensitive() {
		var headers = Map.of(
				"X-DiceChess-Timestamp", String.valueOf(NOW),
				"X-DiceChess-Signature", Signatures.sign(SECRET, NOW, MINIMAL_TURN));

		assertThat(passiveHandler().handle(headers, MINIMAL_TURN, NOW).status()).isEqualTo(200);
	}

	@Test
	void unknownAndMalformedTypesFailWithoutEchoingAttackerInput() {
		var unknown = passiveHandler().handle(Map.of(), "{\"type\":\"secret-looking-type\"}", NOW);

		assertThat(unknown.status()).isEqualTo(400);
		assertThat(unknown.jsonBody()).isEqualTo("{\"error\":\"unrecognized delivery type\"}");
		assertThat(unknown.jsonBody()).doesNotContain("secret-looking-type");
		assertThat(passiveHandler().handle(Map.of(), "{\"type\":7}", NOW).status()).isEqualTo(400);
		assertThat(passiveHandler().handle(Map.of(), "not json", NOW).status()).isEqualTo(400);
	}

	@Test
	void strategyFailuresAndNullActionsAreBoundedAndRedacted() {
		var throwing = new WebhookHandler(SECRET, context -> {
			throw new RuntimeException("sensitive-strategy-detail");
		});
		var failure = throwing.handle(signedHeaders(MINIMAL_TURN, NOW), MINIMAL_TURN, NOW);

		assertThat(failure.status()).isEqualTo(500);
		assertThat(failure.jsonBody()).isEqualTo("{\"error\":\"strategy failed\"}");
		assertThat(failure.jsonBody()).doesNotContain("sensitive-strategy-detail");

		var nullTurn = new WebhookHandler(SECRET, context -> null)
				.handle(signedHeaders(MINIMAL_TURN, NOW), MINIMAL_TURN, NOW);
		assertThat(nullTurn.status()).isEqualTo(500);

		BotStrategy nullDrawStrategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				return new TurnAction(List.of());
			}

			@Override
			public DrawAction onDrawDecision(DrawDecisionContext context) {
				return null;
			}
		};
		var nullDraw = new WebhookHandler(SECRET, nullDrawStrategy)
				.handle(signedHeaders(MINIMAL_DRAW, NOW), MINIMAL_DRAW, NOW);
		assertThat(nullDraw.status()).isEqualTo(500);
	}

	@Test
	void blankWebhookSecretsAreRejectedAtConstruction() {
		assertThatThrownBy(() -> new WebhookHandler(" \t", context -> new TurnAction(List.of())))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("secret must not be blank");
	}

	@Test
	void publicActionsAndContextsDefensivelyCopyMutableMoveLists() {
		var moves = new ArrayList<>(List.of("e2e4"));
		var action = new TurnAction(moves);
		moves.add("g1f3");
		assertThat(action.moves()).containsExactly("e2e4");
		var immutableMoves = action.moves();
		assertThatThrownBy(() -> immutableMoves.add("g1f3")).isInstanceOf(UnsupportedOperationException.class);

		var path = new ArrayList<>(List.of("e2e4"));
		var paths = new ArrayList<List<String>>();
		paths.add(path);
		var context = new TurnContext("g1", "White", 1, "x", null, paths, false);
		path.add("g1f3");
		paths.clear();
		assertThat(context.legalMoves()).containsExactly(List.of("e2e4"));
		var immutablePath = context.legalMoves().getFirst();
		assertThatThrownBy(() -> immutablePath.add("g1f3"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	void drawDecisionContextIsStructurallyDiceFree() {
		assertThat(DrawDecisionContext.class.getRecordComponents())
				.extracting(component -> component.getName())
				.containsExactly("gameId", "seat", "version", "dfen", "clock");
	}

	@Test
	void playApiOpportunityFixtureDispatchesOnDoubleOpportunityAndSerializesRollByDefault() {
		var response = passiveHandler()
				.handle(signedHeaders(DOUBLE_OPPORTUNITY_FIXTURE, NOW), DOUBLE_OPPORTUNITY_FIXTURE, NOW);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.jsonBody()).isEqualTo("{\"decisionId\":\"double_01K4F4Y7M8R2\",\"offerDouble\":false}");
	}

	@Test
	void playApiOpportunityFixtureMapsAllFieldsAndSerializesOfferTrueWhenStrategyOffers() {
		var seen = new AtomicReference<DoubleOpportunityContext>();
		var strategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				return new TurnAction(List.of());
			}

			@Override
			public DoubleOfferAction onDoubleOpportunity(DoubleOpportunityContext context) {
				seen.set(context);
				return DoubleOfferAction.offer();
			}
		};
		var handler = new WebhookHandler(SECRET, strategy);
		var response = handler.handle(signedHeaders(DOUBLE_OPPORTUNITY_FIXTURE, NOW), DOUBLE_OPPORTUNITY_FIXTURE, NOW);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.jsonBody()).isEqualTo("{\"decisionId\":\"double_01K4F4Y7M8R2\",\"offerDouble\":true}");

		var ctx = seen.get();
		assertThat(ctx).isNotNull();
		assertThat(ctx.gameId()).isEqualTo("3f0e2b4c-9a17-4d6e-8b52-1c7d9e0f4a68");
		assertThat(ctx.seat()).isEqualTo("White");
		assertThat(ctx.version()).isEqualTo(0L);
		assertThat(ctx.dfen()).isEqualTo("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
		assertThat(ctx.decisionId()).isEqualTo("double_01K4F4Y7M8R2");
		assertThat(ctx.currency()).isEqualTo("PLAY_CREDIT");
		assertThat(ctx.initialStake()).isEqualTo(10L);
		assertThat(ctx.currentStake()).isEqualTo(10L);
		assertThat(ctx.proposedStake()).isEqualTo(20L);
		assertThat(ctx.cubeValue()).isEqualTo(1);
		assertThat(ctx.cubeOwner()).isNull();
		assertThat(ctx.maximumMultiplier()).isEqualTo(64);
		assertThat(ctx.mayOfferDouble()).isTrue();
		assertThat(ctx.turnSeat()).isEqualTo("White");
		assertThat(ctx.clock()).isEqualTo(new GameClock(300000, 300000, 3000L));
		assertThat(ctx.decision()).isEqualTo(new DoublingDecision.Offer("double_01K4F4Y7M8R2", "White", 20L));
	}

	@Test
	void playApiDecisionFixtureDispatchesOnDoubleDecisionAndSerializesDeclineByDefault() {
		var response = passiveHandler()
				.handle(signedHeaders(DOUBLE_DECISION_FIXTURE, NOW), DOUBLE_DECISION_FIXTURE, NOW);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.jsonBody()).isEqualTo("{\"decisionId\":\"double_01K4F4Y7M8R2\",\"acceptDouble\":false}");
	}

	@Test
	void playApiDecisionFixtureMapsAllFieldsAndSerializesAcceptTrueWhenStrategyTakes() {
		var seen = new AtomicReference<DoubleDecisionContext>();
		var strategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				return new TurnAction(List.of());
			}

			@Override
			public DoubleResponseAction onDoubleDecision(DoubleDecisionContext context) {
				seen.set(context);
				return DoubleResponseAction.accept();
			}
		};
		var handler = new WebhookHandler(SECRET, strategy);
		var response = handler.handle(signedHeaders(DOUBLE_DECISION_FIXTURE, NOW), DOUBLE_DECISION_FIXTURE, NOW);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.jsonBody()).isEqualTo("{\"decisionId\":\"double_01K4F4Y7M8R2\",\"acceptDouble\":true}");

		var ctx = seen.get();
		assertThat(ctx).isNotNull();
		assertThat(ctx.gameId()).isEqualTo("3f0e2b4c-9a17-4d6e-8b52-1c7d9e0f4a68");
		assertThat(ctx.seat()).isEqualTo("Black");
		assertThat(ctx.version()).isEqualTo(1L);
		assertThat(ctx.dfen()).isEqualTo("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
		assertThat(ctx.decisionId()).isEqualTo("double_01K4F4Y7M8R2");
		assertThat(ctx.offeredBy()).isEqualTo("White");
		assertThat(ctx.turnSeat()).isEqualTo("White");
		assertThat(ctx.currency()).isEqualTo("PLAY_CREDIT");
		assertThat(ctx.initialStake()).isEqualTo(10L);
		assertThat(ctx.currentStake()).isEqualTo(10L);
		assertThat(ctx.proposedStake()).isEqualTo(20L);
		assertThat(ctx.cubeValue()).isEqualTo(1);
		assertThat(ctx.cubeOwner()).isNull();
		assertThat(ctx.maximumMultiplier()).isEqualTo(64);
		assertThat(ctx.mayOfferDouble()).isFalse();
		assertThat(ctx.clock()).isEqualTo(new GameClock(300000, 295000, 3000L));
		assertThat(ctx.decision()).isEqualTo(new DoublingDecision.Response("double_01K4F4Y7M8R2", "Black", "White", 20L));
	}

	@Test
	void doubleDropActionSerializesAcceptFalse() {
		var strategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				return new TurnAction(List.of());
			}

			@Override
			public DoubleResponseAction onDoubleDecision(DoubleDecisionContext context) {
				return DoubleResponseAction.drop();
			}
		};
		var handler = new WebhookHandler(SECRET, strategy);
		var response = handler.handle(signedHeaders(DOUBLE_DECISION_FIXTURE, NOW), DOUBLE_DECISION_FIXTURE, NOW);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.jsonBody()).isEqualTo("{\"decisionId\":\"double_01K4F4Y7M8R2\",\"acceptDouble\":false}");
	}

	@Test
	void repeatedDoubleDeliveriesAreIdempotent() {
		var opportunityResponse1 = passiveHandler()
				.handle(signedHeaders(DOUBLE_OPPORTUNITY_FIXTURE, NOW), DOUBLE_OPPORTUNITY_FIXTURE, NOW);
		var opportunityResponse2 = passiveHandler()
				.handle(signedHeaders(DOUBLE_OPPORTUNITY_FIXTURE, NOW), DOUBLE_OPPORTUNITY_FIXTURE, NOW);
		assertThat(opportunityResponse1.status()).isEqualTo(200);
		assertThat(opportunityResponse2.status()).isEqualTo(200);
		assertThat(opportunityResponse1.jsonBody()).isEqualTo(opportunityResponse2.jsonBody());

		var decisionResponse1 = passiveHandler()
				.handle(signedHeaders(DOUBLE_DECISION_FIXTURE, NOW), DOUBLE_DECISION_FIXTURE, NOW);
		var decisionResponse2 = passiveHandler()
				.handle(signedHeaders(DOUBLE_DECISION_FIXTURE, NOW), DOUBLE_DECISION_FIXTURE, NOW);
		assertThat(decisionResponse1.status()).isEqualTo(200);
		assertThat(decisionResponse2.status()).isEqualTo(200);
		assertThat(decisionResponse1.jsonBody()).isEqualTo(decisionResponse2.jsonBody());
	}

	@Test
	void tamperedSignaturesOnDoubleDeliveriesAreRejectedWith401() {
		assertThat(passiveHandler().handle(Map.of(), DOUBLE_OPPORTUNITY_FIXTURE, NOW).status()).isEqualTo(401);
		assertThat(passiveHandler().handle(Map.of(), DOUBLE_DECISION_FIXTURE, NOW).status()).isEqualTo(401);

		var tamperedOpportunity = Map.of(
				WebhookHandler.TIMESTAMP_HEADER, String.valueOf(NOW),
				WebhookHandler.SIGNATURE_HEADER, "deadbeef");
		assertThat(passiveHandler().handle(tamperedOpportunity, DOUBLE_OPPORTUNITY_FIXTURE, NOW).status())
				.isEqualTo(401);

		var expiredOpportunity = signedHeaders(DOUBLE_OPPORTUNITY_FIXTURE, NOW - 3600);
		assertThat(passiveHandler().handle(expiredOpportunity, DOUBLE_OPPORTUNITY_FIXTURE, NOW).status())
				.isEqualTo(401);
	}

	@Test
	void malformedStakeAndCubeFieldsFailClosedWith400() {
		var calls = new AtomicInteger();
		var strategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				calls.incrementAndGet();
				return new TurnAction(List.of());
			}

			@Override
			public DoubleOfferAction onDoubleOpportunity(DoubleOpportunityContext context) {
				calls.incrementAndGet();
				return DoubleOfferAction.offer();
			}

			@Override
			public DoubleResponseAction onDoubleDecision(DoubleDecisionContext context) {
				calls.incrementAndGet();
				return DoubleResponseAction.accept();
			}
		};
		var handler = new WebhookHandler(SECRET, strategy);

		var invalidOpportunityBodies = List.of(
				MINIMAL_OPPORTUNITY.replace("\"dicePending\":false", "\"dicePending\":true"),
				MINIMAL_OPPORTUNITY.replace("\"activeSeat\":\"White\"", "\"activeSeat\":\"Black\""),
				MINIMAL_OPPORTUNITY.replace("\"currency\":\"PLAY_CREDIT\"", "\"currency\":\"USD\""),
				MINIMAL_OPPORTUNITY.replace("\"cubeValue\":1", "\"cubeValue\":3"),
				MINIMAL_OPPORTUNITY.replace("\"cubeOwner\":null", "\"cubeOwner\":\"White\""),
				MINIMAL_OPPORTUNITY.replace("\"maximumMultiplier\":64", "\"maximumMultiplier\":10"),
				MINIMAL_OPPORTUNITY.replace("\"proposedStake\":20", "\"proposedStake\":30"),
				MINIMAL_OPPORTUNITY.replace("\"kind\":\"offer\"", "\"kind\":\"response\""),
				MINIMAL_OPPORTUNITY.replace("\"mayOfferDouble\":true", "\"mayOfferDouble\":false"),
				MINIMAL_OPPORTUNITY.replace("\"id\":\"double_01K4F4Y7M8R2\"", "\"id\":\"invalid-id\""),
				MINIMAL_OPPORTUNITY.replace(",\"doubling\":{", ",\"legalMoves\":{\"e2e4\":{}},\"doubling\":{"),
				MINIMAL_OPPORTUNITY.replace("0 1", "0 1 NBK"));

		for (var body : invalidOpportunityBodies) {
			assertThat(handler.handle(signedHeaders(body, NOW), body, NOW).status()).as(body).isEqualTo(400);
		}

		var invalidDecisionBodies = List.of(
				MINIMAL_DOUBLE_DECISION.replace("\"dicePending\":false", "\"dicePending\":true"),
				MINIMAL_DOUBLE_DECISION.replace("\"activeSeat\":\"Black\"", "\"activeSeat\":\"White\""),
				MINIMAL_DOUBLE_DECISION.replace("\"currency\":\"PLAY_CREDIT\"", "\"currency\":\"EUR\""),
				MINIMAL_DOUBLE_DECISION.replace("\"cubeValue\":1", "\"cubeValue\":5"),
				MINIMAL_DOUBLE_DECISION.replace("\"cubeValue\":1,\"cubeOwner\":null", "\"cubeValue\":2,\"cubeOwner\":null"),
				MINIMAL_DOUBLE_DECISION.replace("\"proposedStake\":20", "\"proposedStake\":25"),
				MINIMAL_DOUBLE_DECISION.replace("\"kind\":\"response\"", "\"kind\":\"offer\""),
				MINIMAL_DOUBLE_DECISION.replace("\"mayOfferDouble\":false", "\"mayOfferDouble\":true"),
				MINIMAL_DOUBLE_DECISION.replace("\"offeredBy\":\"White\"", "\"offeredBy\":\"Black\""),
				MINIMAL_DOUBLE_DECISION.replace("\"id\":\"double_01K4F4Y7M8R2\"", "\"id\":\"\""),
				MINIMAL_DOUBLE_DECISION.replace(",\"doubling\":{", ",\"legalMoves\":{\"e2e4\":{}},\"doubling\":{"),
				MINIMAL_DOUBLE_DECISION.replace("0 1", "0 1 NBK"));

		for (var body : invalidDecisionBodies) {
			assertThat(handler.handle(signedHeaders(body, NOW), body, NOW).status()).as(body).isEqualTo(400);
		}

		assertThat(calls).hasValue(0);
	}

	@Test
	void strategyFailuresAndNullActionsInDoublingAre500() {
		var throwingStrategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				return new TurnAction(List.of());
			}

			@Override
			public DoubleOfferAction onDoubleOpportunity(DoubleOpportunityContext context) {
				throw new RuntimeException("sensitive-opportunity-failure");
			}

			@Override
			public DoubleResponseAction onDoubleDecision(DoubleDecisionContext context) {
				throw new RuntimeException("sensitive-decision-failure");
			}
		};
		var throwingHandler = new WebhookHandler(SECRET, throwingStrategy);

		var oppFailure = throwingHandler.handle(signedHeaders(MINIMAL_OPPORTUNITY, NOW), MINIMAL_OPPORTUNITY, NOW);
		assertThat(oppFailure.status()).isEqualTo(500);
		assertThat(oppFailure.jsonBody()).isEqualTo("{\"error\":\"strategy failed\"}");
		assertThat(oppFailure.jsonBody()).doesNotContain("sensitive-opportunity-failure");

		var decFailure = throwingHandler.handle(signedHeaders(MINIMAL_DOUBLE_DECISION, NOW), MINIMAL_DOUBLE_DECISION, NOW);
		assertThat(decFailure.status()).isEqualTo(500);
		assertThat(decFailure.jsonBody()).isEqualTo("{\"error\":\"strategy failed\"}");
		assertThat(decFailure.jsonBody()).doesNotContain("sensitive-decision-failure");

		var nullStrategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				return new TurnAction(List.of());
			}

			@Override
			public DoubleOfferAction onDoubleOpportunity(DoubleOpportunityContext context) {
				return null;
			}

			@Override
			public DoubleResponseAction onDoubleDecision(DoubleDecisionContext context) {
				return null;
			}
		};
		var nullHandler = new WebhookHandler(SECRET, nullStrategy);
		assertThat(nullHandler.handle(signedHeaders(MINIMAL_OPPORTUNITY, NOW), MINIMAL_OPPORTUNITY, NOW).status())
				.isEqualTo(500);
		assertThat(nullHandler.handle(signedHeaders(MINIMAL_DOUBLE_DECISION, NOW), MINIMAL_DOUBLE_DECISION, NOW).status())
				.isEqualTo(500);
	}

	@Test
	void unlimitedGameClockParsesAsNullForDoubling() {
		var seenOpp = new AtomicReference<DoubleOpportunityContext>();
		var seenDec = new AtomicReference<DoubleDecisionContext>();
		var strategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				return new TurnAction(List.of());
			}

			@Override
			public DoubleOfferAction onDoubleOpportunity(DoubleOpportunityContext context) {
				seenOpp.set(context);
				return DoubleOfferAction.roll();
			}

			@Override
			public DoubleResponseAction onDoubleDecision(DoubleDecisionContext context) {
				seenDec.set(context);
				return DoubleResponseAction.decline();
			}
		};
		var handler = new WebhookHandler(SECRET, strategy);

		var oppBody = DOUBLE_OPPORTUNITY_FIXTURE.replace("\"clocks\":{\"white\":300000,\"black\":300000}", "\"clocks\":null");
		var oppResp = handler.handle(signedHeaders(oppBody, NOW), oppBody, NOW);
		assertThat(oppResp.status()).isEqualTo(200);
		assertThat(seenOpp.get().clock()).isNull();

		var decBody = DOUBLE_DECISION_FIXTURE.replace("\"clocks\":{\"white\":295000,\"black\":300000}", "\"clocks\":null");
		var decResp = handler.handle(signedHeaders(decBody, NOW), decBody, NOW);
		assertThat(decResp.status()).isEqualTo(200);
		assertThat(seenDec.get().clock()).isNull();
	}

	@Test
	void doublingContextsAreStructurallyDiceFree() {
		assertThat(DoubleOpportunityContext.class.getRecordComponents())
				.extracting(component -> component.getName())
				.containsExactly("gameId", "seat", "version", "dfen", "clock", "doubling");

		assertThat(DoubleDecisionContext.class.getRecordComponents())
				.extracting(component -> component.getName())
				.containsExactly("gameId", "seat", "version", "dfen", "clock", "doubling");
	}

	private static HttpServer stubMovesEndpoint(String gameId, String responseBody, AtomicInteger calls)
			throws IOException {
		var server = HttpServer.create(new InetSocketAddress(0), 0);
		server.createContext("/games/" + gameId + "/moves", exchange -> {
			calls.incrementAndGet();
			var bytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, bytes.length);
			try (var out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		});
		server.start();
		return server;
	}
}
