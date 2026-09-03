package com.fortemate.dicechess.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Proves the full handler stack through a real socket and HTTP client. */
class CustomHandlerServerTest {

	private static final String SECRET = "test-webhook-secret";

	@Test
	void handshakeTurnAndDrawDecisionRunOverRealHttp() throws Exception {
		BotStrategy strategy = new BotStrategy() {
			@Override
			public TurnAction onTurn(TurnContext context) {
				return new TurnAction(List.of("b1c3"), true);
			}

			@Override
			public DrawAction onDrawDecision(DrawDecisionContext context) {
				return DrawAction.accept();
			}

			@Override
			public DoubleOfferAction onDoubleOpportunity(DoubleOpportunityContext context) {
				return DoubleOfferAction.offer();
			}

			@Override
			public DoubleResponseAction onDoubleDecision(DoubleDecisionContext context) {
				return DoubleResponseAction.accept();
			}
		};
		var server = CustomHandlerServer.start(0, "/api/webhook", new WebhookHandler(SECRET, strategy));
		try {
			var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/webhook");
			var client = HttpClient.newHttpClient();

			var handshakeBody = "{\"type\":\"verification\",\"nonce\":\"live-1\"}";
			var handshake = post(client, uri, handshakeBody, null);
			assertThat(handshake.statusCode()).isEqualTo(200);
			assertThat(handshake.body()).isEqualTo("{\"nonce\":\"live-1\"}");

			var turnBody =
					"{\"type\":\"yourTurn\",\"gameId\":\"g1\",\"seat\":\"White\",\"state\":{\"version\":4,\"dfen\":\"x\",\"activeSeat\":\"White\",\"dicePending\":true,\"legalMoves\":{\"b1c3\":{}},\"mayOfferDraw\":true}}";
			var now = System.currentTimeMillis() / 1000;
			var turn = post(client, uri, turnBody, now);
			assertThat(turn.statusCode()).isEqualTo(200);
			assertThat(turn.body()).isEqualTo("{\"moves\":[\"b1c3\"],\"offerDraw\":true}");

			var drawBody =
					"{\"type\":\"drawDecision\",\"gameId\":\"g1\",\"seat\":\"White\",\"state\":{\"version\":5,\"dfen\":\"pre-roll\",\"activeSeat\":\"White\",\"dicePending\":false,\"drawOffer\":{\"pending\":true}}}";
			var draw = post(client, uri, drawBody, now);
			assertThat(draw.statusCode()).isEqualTo(200);
			assertThat(draw.body()).isEqualTo("{\"acceptDraw\":true}");

			var oppBody =
					"{\"type\":\"doubleOpportunity\",\"gameId\":\"g1\",\"seat\":\"White\",\"state\":{\"version\":6,\"dfen\":\"x\",\"activeSeat\":\"White\",\"dicePending\":false,\"doubling\":{\"currency\":\"PLAY_CREDIT\",\"initialStake\":10,\"currentStake\":10,\"cubeValue\":1,\"cubeOwner\":null,\"maximumMultiplier\":64,\"mayOfferDouble\":true,\"turnSeat\":\"White\",\"decision\":{\"id\":\"double_01K4F4Y7M8R2\",\"kind\":\"offer\",\"seat\":\"White\",\"proposedStake\":20}}}}";
			var opp = post(client, uri, oppBody, now);
			assertThat(opp.statusCode()).isEqualTo(200);
			assertThat(opp.body()).isEqualTo("{\"decisionId\":\"double_01K4F4Y7M8R2\",\"offerDouble\":true}");

			var decBody =
					"{\"type\":\"doubleDecision\",\"gameId\":\"g1\",\"seat\":\"Black\",\"state\":{\"version\":7,\"dfen\":\"x\",\"activeSeat\":\"Black\",\"dicePending\":false,\"doubling\":{\"currency\":\"PLAY_CREDIT\",\"initialStake\":10,\"currentStake\":10,\"cubeValue\":1,\"cubeOwner\":null,\"maximumMultiplier\":64,\"mayOfferDouble\":false,\"turnSeat\":\"White\",\"decision\":{\"id\":\"double_01K4F4Y7M8R2\",\"kind\":\"response\",\"seat\":\"Black\",\"offeredBy\":\"White\",\"proposedStake\":20}}}}";
			var dec = post(client, uri, decBody, now);
			assertThat(dec.statusCode()).isEqualTo(200);
			assertThat(dec.body()).isEqualTo("{\"decisionId\":\"double_01K4F4Y7M8R2\",\"acceptDouble\":true}");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void pendingOnlyVerificationV2RunsOverRealHttp() throws Exception {
		var keys = WebhookKeys.pendingOnly(VerificationV2Vectors.CANONICAL_SECRET);
		var handler = new WebhookHandler(keys, context -> new TurnAction(List.of()));
		var server = CustomHandlerServer.start(0, "/api/webhook", handler);
		try {
			var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/webhook");
			var client = HttpClient.newHttpClient();

			var now = System.currentTimeMillis() / 1000;
			var signature = Signatures.sign(VerificationV2Vectors.CANONICAL_SECRET, now, VerificationV2Vectors.CANONICAL_RAW_BODY);
			var expectedProof = Signatures.activationProof(VerificationV2Vectors.CANONICAL_SECRET, VerificationV2Vectors.CANONICAL_RAW_BODY);

			var request = HttpRequest.newBuilder(uri)
					.header(WebhookHandler.TIMESTAMP_HEADER, String.valueOf(now))
					.header(WebhookHandler.SIGNATURE_HEADER, signature)
					.POST(HttpRequest.BodyPublishers.ofString(VerificationV2Vectors.CANONICAL_RAW_BODY))
					.build();

			var response = client.send(request, HttpResponse.BodyHandlers.ofString());
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.body()).isEqualTo(
					"{\"nonce\":\"" + VerificationV2Vectors.CANONICAL_NONCE + "\",\"proof\":\"" + expectedProof + "\"}");
		} finally {
			server.stop(0);
		}
	}

	@Test
	void dualKeyRotationRunsOverRealHttp() throws Exception {
		var activeSecret = "active-secret-1234567890abcdef1234567890abcdef1234567890abcdef1234";
		var pendingSecret = VerificationV2Vectors.CANONICAL_SECRET;
		var keys = WebhookKeys.activeAndPending(activeSecret, pendingSecret);
		var handler = new WebhookHandler(keys, context -> new TurnAction(List.of("e2e4")));
		var server = CustomHandlerServer.start(0, "/api/webhook", handler);
		try {
			var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api/webhook");
			var client = HttpClient.newHttpClient();

			var now = System.currentTimeMillis() / 1000;
			var v2Sig = Signatures.sign(pendingSecret, now, VerificationV2Vectors.CANONICAL_RAW_BODY);
			var expectedProof = Signatures.activationProof(pendingSecret, VerificationV2Vectors.CANONICAL_RAW_BODY);

			// 1. Activation challenge with pending key
			var v2Request = HttpRequest.newBuilder(uri)
					.header(WebhookHandler.TIMESTAMP_HEADER, String.valueOf(now))
					.header(WebhookHandler.SIGNATURE_HEADER, v2Sig)
					.POST(HttpRequest.BodyPublishers.ofString(VerificationV2Vectors.CANONICAL_RAW_BODY))
					.build();
			var v2Response = client.send(v2Request, HttpResponse.BodyHandlers.ofString());
			assertThat(v2Response.statusCode()).isEqualTo(200);
			assertThat(v2Response.body()).isEqualTo(
					"{\"nonce\":\"" + VerificationV2Vectors.CANONICAL_NONCE + "\",\"proof\":\"" + expectedProof + "\"}");

			// 2. Gameplay delivery signed with active key
			var turnBody = "{\"type\":\"yourTurn\",\"gameId\":\"g1\",\"seat\":\"White\",\"state\":{\"version\":1,\"dfen\":\"x\",\"activeSeat\":\"White\",\"dicePending\":true}}";
			var activeTurnReq = HttpRequest.newBuilder(uri)
					.header(WebhookHandler.TIMESTAMP_HEADER, String.valueOf(now))
					.header(WebhookHandler.SIGNATURE_HEADER, Signatures.sign(activeSecret, now, turnBody))
					.POST(HttpRequest.BodyPublishers.ofString(turnBody))
					.build();
			var activeTurnResp = client.send(activeTurnReq, HttpResponse.BodyHandlers.ofString());
			assertThat(activeTurnResp.statusCode()).isEqualTo(200);

			// 3. Gameplay delivery signed with pending key
			var pendingTurnReq = HttpRequest.newBuilder(uri)
					.header(WebhookHandler.TIMESTAMP_HEADER, String.valueOf(now))
					.header(WebhookHandler.SIGNATURE_HEADER, Signatures.sign(pendingSecret, now, turnBody))
					.POST(HttpRequest.BodyPublishers.ofString(turnBody))
					.build();
			var pendingTurnResp = client.send(pendingTurnReq, HttpResponse.BodyHandlers.ofString());
			assertThat(pendingTurnResp.statusCode()).isEqualTo(200);

			// 4. Gameplay delivery signed with invalid secret is rejected
			var invalidTurnReq = HttpRequest.newBuilder(uri)
					.header(WebhookHandler.TIMESTAMP_HEADER, String.valueOf(now))
					.header(WebhookHandler.SIGNATURE_HEADER, Signatures.sign("invalid-key", now, turnBody))
					.POST(HttpRequest.BodyPublishers.ofString(turnBody))
					.build();
			var invalidTurnResp = client.send(invalidTurnReq, HttpResponse.BodyHandlers.ofString());
			assertThat(invalidTurnResp.statusCode()).isEqualTo(401);
		} finally {
			server.stop(0);
		}
	}

	private static HttpResponse<String> post(HttpClient client, URI uri, String body, Long timestamp) throws Exception {
		var request = HttpRequest.newBuilder(uri);
		if (timestamp != null) {
			request.header(WebhookHandler.TIMESTAMP_HEADER, String.valueOf(timestamp));
			request.header(WebhookHandler.SIGNATURE_HEADER, Signatures.sign(SECRET, timestamp, body));
		}
		return client.send(
				request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
}
