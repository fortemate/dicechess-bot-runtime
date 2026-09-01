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
