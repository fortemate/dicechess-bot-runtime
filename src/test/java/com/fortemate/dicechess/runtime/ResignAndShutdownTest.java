package com.fortemate.dicechess.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ResignAndShutdownTest {

	private static final String SECRET = "test-webhook-secret";
	private static final long NOW = 1752750000L;
	private static final Gson GSON = new Gson();

	private static final String MINIMAL_TURN =
			"{\"type\":\"yourTurn\",\"gameId\":\"g1\",\"seat\":\"White\",\"state\":{\"version\":1,\"dfen\":\"x\",\"activeSeat\":\"White\",\"dicePending\":true}}";

	private static final String MINIMAL_DRAW =
			"{\"type\":\"drawDecision\",\"gameId\":\"g1\",\"seat\":\"Black\",\"state\":{\"version\":2,\"dfen\":\"pre-roll\",\"activeSeat\":\"Black\",\"dicePending\":false,\"drawOffer\":{\"pending\":true}}}";

	private static final String MINIMAL_OPPORTUNITY = """
			{"type":"doubleOpportunity","gameId":"g1","seat":"White","state":{"version":0,"dfen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","activeSeat":"White","dicePending":false,"doubling":{"currency":"PLAY_CREDIT","initialStake":10,"currentStake":10,"cubeValue":1,"cubeOwner":null,"maximumMultiplier":64,"mayOfferDouble":true,"turnSeat":"White","decision":{"id":"double_01K4F4Y7M8R2","kind":"offer","seat":"White","proposedStake":20}}}}
			""".strip();

	private static final String MINIMAL_DOUBLE_DECISION = """
			{"type":"doubleDecision","gameId":"g1","seat":"Black","state":{"version":1,"dfen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","activeSeat":"Black","dicePending":false,"doubling":{"currency":"PLAY_CREDIT","initialStake":10,"currentStake":10,"cubeValue":1,"cubeOwner":null,"maximumMultiplier":64,"mayOfferDouble":false,"turnSeat":"White","decision":{"id":"double_01K4F4Y7M8R2","kind":"response","seat":"Black","offeredBy":"White","proposedStake":20}}}}
			""".strip();

	private static Map<String, String> signedHeaders(String body, long timestamp) {
		return Map.of(
				WebhookHandler.TIMESTAMP_HEADER, String.valueOf(timestamp),
				WebhookHandler.SIGNATURE_HEADER, Signatures.sign(SECRET, timestamp, body));
	}

	@Test
	void turnActionResignSerializesResignTrueAndSafeDefaults() {
		var action = TurnAction.resign();
		var json = GSON.toJson(action);

		assertThat(json).isEqualTo("{\"moves\":[],\"offerDraw\":false,\"resign\":true}");
	}

	@Test
	void drawActionResignSerializesResignTrueAndSafeDefaults() {
		var action = DrawAction.resign();
		var json = GSON.toJson(action);

		assertThat(json).isEqualTo("{\"acceptDraw\":false,\"resign\":true}");
	}

	@Test
	void doubleOfferActionResignSerializesResignTrueAndSafeDefaults() {
		var action = DoubleOfferAction.resign();
		var json = GSON.toJson(action);

		assertThat(json).isEqualTo("{\"offerDouble\":false,\"resign\":true}");
	}

	@Test
	void doubleResponseActionResignSerializesResignTrueAndSafeDefaults() {
		var action = DoubleResponseAction.resign();
		var json = GSON.toJson(action);

		assertThat(json).isEqualTo("{\"acceptDouble\":false,\"resign\":true}");
	}

	@Test
	void strategyReturningResignSerializesResignTrueAcrossAllFourDeliveries() {
		BotStrategy strategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				return TurnAction.resign();
			}

			@Override
			public DrawAction onDrawDecision(DrawDecisionContext context) {
				return DrawAction.resign();
			}

			@Override
			public DoubleOfferAction onDoubleOpportunity(DoubleOpportunityContext context) {
				return DoubleOfferAction.resign();
			}

			@Override
			public DoubleResponseAction onDoubleDecision(DoubleDecisionContext context) {
				return DoubleResponseAction.resign();
			}
		};
		var handler = new WebhookHandler(SECRET, strategy);

		var turnResp = handler.handle(signedHeaders(MINIMAL_TURN, NOW), MINIMAL_TURN, NOW);
		assertThat(turnResp.status()).isEqualTo(200);
		assertThat(turnResp.jsonBody()).isEqualTo("{\"moves\":[],\"offerDraw\":false,\"resign\":true}");

		var drawResp = handler.handle(signedHeaders(MINIMAL_DRAW, NOW), MINIMAL_DRAW, NOW);
		assertThat(drawResp.status()).isEqualTo(200);
		assertThat(drawResp.jsonBody()).isEqualTo("{\"acceptDraw\":false,\"resign\":true}");

		var oppResp = handler.handle(signedHeaders(MINIMAL_OPPORTUNITY, NOW), MINIMAL_OPPORTUNITY, NOW);
		assertThat(oppResp.status()).isEqualTo(200);
		assertThat(oppResp.jsonBody()).isEqualTo("{\"decisionId\":\"double_01K4F4Y7M8R2\",\"offerDouble\":false,\"resign\":true}");

		var decResp = handler.handle(signedHeaders(MINIMAL_DOUBLE_DECISION, NOW), MINIMAL_DOUBLE_DECISION, NOW);
		assertThat(decResp.status()).isEqualTo(200);
		assertThat(decResp.jsonBody()).isEqualTo("{\"decisionId\":\"double_01K4F4Y7M8R2\",\"acceptDouble\":false,\"resign\":true}");
	}

	@Test
	void resignPolicyTruePrecedesStrategyDispatchAcrossAllDeliveries() {
		var calls = new AtomicInteger(0);
		BotStrategy strategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				calls.incrementAndGet();
				return new TurnAction(List.of("e2e4"));
			}

			@Override
			public DrawAction onDrawDecision(DrawDecisionContext context) {
				calls.incrementAndGet();
				return DrawAction.accept();
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

		ResignPolicy resignPolicy = () -> true;
		var handler = new WebhookHandler(SECRET, null, strategy, resignPolicy);

		var turnResp = handler.handle(signedHeaders(MINIMAL_TURN, NOW), MINIMAL_TURN, NOW);
		assertThat(turnResp.status()).isEqualTo(200);
		assertThat(turnResp.jsonBody()).isEqualTo("{\"moves\":[],\"offerDraw\":false,\"resign\":true}");

		var drawResp = handler.handle(signedHeaders(MINIMAL_DRAW, NOW), MINIMAL_DRAW, NOW);
		assertThat(drawResp.status()).isEqualTo(200);
		assertThat(drawResp.jsonBody()).isEqualTo("{\"acceptDraw\":false,\"resign\":true}");

		var oppResp = handler.handle(signedHeaders(MINIMAL_OPPORTUNITY, NOW), MINIMAL_OPPORTUNITY, NOW);
		assertThat(oppResp.status()).isEqualTo(200);
		assertThat(oppResp.jsonBody()).isEqualTo("{\"decisionId\":\"double_01K4F4Y7M8R2\",\"offerDouble\":false,\"resign\":true}");

		var decResp = handler.handle(signedHeaders(MINIMAL_DOUBLE_DECISION, NOW), MINIMAL_DOUBLE_DECISION, NOW);
		assertThat(decResp.status()).isEqualTo(200);
		assertThat(decResp.jsonBody()).isEqualTo("{\"decisionId\":\"double_01K4F4Y7M8R2\",\"acceptDouble\":false,\"resign\":true}");

		assertThat(calls.get()).isZero();
	}

	@Test
	void resignPolicyPreservesHmacAuthenticationRequirement() {
		ResignPolicy resignPolicy = () -> true;
		var handler = new WebhookHandler(SECRET, null, context -> new TurnAction(List.of()), resignPolicy);

		// Missing/bad signature must fail with 401 even when resignPolicy is true
		var unauthenticatedResp = handler.handle(Map.of(), MINIMAL_TURN, NOW);
		assertThat(unauthenticatedResp.status()).isEqualTo(401);

		var badSigResp = handler.handle(
				Map.of(WebhookHandler.TIMESTAMP_HEADER, String.valueOf(NOW), WebhookHandler.SIGNATURE_HEADER, "bad"),
				MINIMAL_TURN,
				NOW);
		assertThat(badSigResp.status()).isEqualTo(401);
	}

	@Test
	void shutdownWithBotTokenCallsResignAllRouteAndStopsServer() throws Exception {
		var authHeader = new AtomicReference<String>();
		var requestBody = new AtomicReference<String>();

		var fakePlayApi = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
		fakePlayApi.createContext("/bot/games/resign-all", exchange -> {
			authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
			requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			var resp = "{\"count\":3}";
			exchange.sendResponseHeaders(200, resp.length());
			try (var out = exchange.getResponseBody()) {
				out.write(resp.getBytes(StandardCharsets.UTF_8));
			}
		});
		fakePlayApi.start();

		try {
			var baseUrl = "http://127.0.0.1:" + fakePlayApi.getAddress().getPort();
			var handler = new WebhookHandler(SECRET, context -> new TurnAction(List.of()));
			var server = CustomHandlerServer.start(0, "/api/webhook", handler);

			CustomHandlerServer.executeShutdown(server, new AtomicBoolean(false), "secret-bot-token-123", baseUrl, 0L, false);

			assertThat(authHeader.get()).isEqualTo("Bearer secret-bot-token-123");
			assertThat(requestBody.get()).isEqualTo("{\"pauseSeating\":true}");
		} finally {
			fakePlayApi.stop(0);
		}
	}

	@Test
	void shutdownWithoutBotTokenEntersDrainModeThenStopsServer() throws Exception {
		var handler = new WebhookHandler(SECRET, context -> new TurnAction(List.of("e2e4")));
		var isDraining = new AtomicBoolean(false);
		var server = CustomHandlerServer.start(0, "/api/webhook", handler, isDraining);

		try {
			// Before shutdown execution, normal strategy returns move
			var normalResp = handler.handle(signedHeaders(MINIMAL_TURN, NOW), MINIMAL_TURN, NOW);
			assertThat(normalResp.jsonBody()).isEqualTo("{\"moves\":[\"e2e4\"],\"offerDraw\":false}");

			// Execute shutdown in background thread without bot token and 1s drain
			var shutdownThread = new Thread(() ->
					CustomHandlerServer.executeShutdown(server, isDraining, null, "http://localhost", 1L, false));
			shutdownThread.start();

			// Give thread moment to set isDraining
			Thread.sleep(100);
			assertThat(isDraining.get()).isTrue();

			shutdownThread.join(3000);
			assertThat(shutdownThread.isAlive()).isFalse();
		} finally {
			server.stop(0);
		}
	}
}
