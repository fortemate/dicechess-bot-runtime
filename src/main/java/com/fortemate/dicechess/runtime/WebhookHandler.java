package com.fortemate.dicechess.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Authenticates and dispatches DiceChess webhook deliveries to a {@link BotStrategy}.
 *
 * <p>The unsigned legacy ownership handshake is preserved. Game decisions are authenticated over
 * the exact raw body before any decision-specific state is read. A strategy runtime exception is
 * converted to a bounded error response and its message is never returned to the caller.
 */
public final class WebhookHandler {

	/** Header carrying the delivery's Unix-epoch-seconds timestamp. */
	public static final String TIMESTAMP_HEADER = "x-dicechess-timestamp";

	/** Header carrying the hex HMAC-SHA256 signature (see {@link Signatures}). */
	public static final String SIGNATURE_HEADER = "x-dicechess-signature";

	private static final String FIELD_CLOCKS = "clocks";
	private static final String FIELD_TIME_CONTROL = "timeControl";
	private static final String VARIANT_FISCHER = "Fischer";

	private static final Gson GSON = new Gson();
	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
	private static final Duration FALLBACK_TIMEOUT = Duration.ofSeconds(5);

	private final String secret;
	private final String playApiBaseUrl;
	private final BotStrategy strategy;

	/**
	 * Creates a handler without the {@code GET /games/{id}/moves} fallback.
	 *
	 * @param secret the webhook secret issued by the platform
	 * @param strategy the decision-oriented bot strategy
	 */
	public WebhookHandler(String secret, BotStrategy strategy) {
		this(secret, null, strategy);
	}

	/**
	 * Creates a handler that fetches {@code GET /games/{id}/moves} when a turn's inline move tree
	 * is capped. The fallback endpoint is public and needs no additional credential.
	 *
	 * @param secret the webhook secret issued by the platform
	 * @param playApiBaseUrl play-api's base URL; a trailing slash is tolerated
	 * @param strategy the decision-oriented bot strategy
	 */
	public WebhookHandler(String secret, String playApiBaseUrl, BotStrategy strategy) {
		var requiredSecret = Objects.requireNonNull(secret, "secret must not be null");
		if (requiredSecret.isBlank()) {
			throw new IllegalArgumentException("secret must not be blank");
		}
		this.secret = requiredSecret;
		this.playApiBaseUrl = playApiBaseUrl == null ? null : stripTrailingSlash(playApiBaseUrl);
		this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
	}

	/**
	 * Handles one webhook request.
	 *
	 * @param headers the request headers; lookup is case-insensitive
	 * @param rawBody the request body exactly as received
	 * @param nowEpochSeconds the verifier's current Unix epoch time in seconds
	 * @return status 200 for a successful handshake/decision, 400 for a malformed or unknown
	 *     envelope, 401 for a missing/expired/wrong signature, or 500 for a strategy failure
	 */
	public Response handle(Map<String, String> headers, String rawBody, long nowEpochSeconds) {
		String type;
		try {
			type = readEnvelopeType(rawBody);
		} catch (RuntimeException e) {
			return error(400, "malformed JSON body");
		}

		if (type.equals("verification")) {
			try {
				var envelope = parseEnvelope(rawBody);
				if (!requiredString(envelope, "type").equals(type)) {
					return error(400, "malformed envelope");
				}
				return handshake(envelope);
			} catch (RuntimeException e) {
				return error(400, "malformed envelope");
			}
		}
		if (!type.equals("yourTurn") && !type.equals("drawDecision")) {
			return error(400, "unrecognized delivery type");
		}

		Response authenticationFailure;
		try {
			authenticationFailure = authenticate(headers, rawBody, nowEpochSeconds);
		} catch (RuntimeException e) {
			return error(401, "malformed signature headers");
		}
		if (authenticationFailure != null) {
			return authenticationFailure;
		}

		try {
			var envelope = parseEnvelope(rawBody);
			if (!requiredString(envelope, "type").equals(type)) {
				return error(400, "malformed envelope");
			}
			return type.equals("yourTurn") ? yourTurn(envelope) : drawDecision(envelope);
		} catch (RuntimeException e) {
			return error(400, "malformed envelope");
		}
	}

	/**
	 * Reads only play-api's canonical first-field discriminator, leaving full state parsing until
	 * after authentication.
	 */
	private static String readEnvelopeType(String rawBody) {
		try (var reader = new JsonReader(new StringReader(rawBody))) {
			if (reader.peek() != JsonToken.BEGIN_OBJECT) {
				throw new IllegalArgumentException("envelope must be an object");
			}
			reader.beginObject();
			if (!reader.hasNext() || !reader.nextName().equals("type")) {
				throw new IllegalArgumentException("type must be the first envelope field");
			}
			if (reader.peek() != JsonToken.STRING) {
				throw new IllegalArgumentException("type must be a string");
			}
			var type = reader.nextString();
			if (type.isBlank()) {
				throw new IllegalArgumentException("type must not be blank");
			}
			return type;
		} catch (IOException e) {
			throw new IllegalArgumentException("malformed JSON body", e);
		}
	}

	private static JsonObject parseEnvelope(String rawBody) {
		var envelope = GSON.fromJson(rawBody, JsonObject.class);
		if (envelope == null) {
			throw new IllegalArgumentException("envelope must be an object");
		}
		return envelope;
	}

	private Response handshake(JsonObject envelope) {
		var nonce = envelope.has("nonce") ? envelope.get("nonce").getAsString() : "";
		var body = new JsonObject();
		body.addProperty("nonce", nonce);
		return new Response(200, GSON.toJson(body));
	}

	private Response authenticate(Map<String, String> headers, String rawBody, long now) {
		var lowercased = lowercaseKeys(headers);
		var timestampHeader = lowercased.get(TIMESTAMP_HEADER);
		var signatureHeader = lowercased.get(SIGNATURE_HEADER);
		if (timestampHeader == null || signatureHeader == null) {
			return error(401, "missing signature headers");
		}

		long timestamp;
		try {
			timestamp = Long.parseLong(timestampHeader);
		} catch (NumberFormatException e) {
			return error(401, "malformed timestamp header");
		}

		if (!Signatures.verify(secret, timestamp, rawBody, signatureHeader, now)) {
			return error(401, "invalid or expired signature");
		}
		return null;
	}

	private Response yourTurn(JsonObject envelope) {
		var parsed = parseDecisionState(envelope, true);
		var legalMoves = legalMoves(parsed.gameId(), parsed.version(), parsed.dfen(), parsed.state());
		var mayOfferDraw = optionalBooleanTrue(parsed.state(), "mayOfferDraw");
		var context = new TurnContext(
				parsed.gameId(),
				parsed.seat(),
				parsed.version(),
				parsed.dfen(),
				parsed.clock(),
				legalMoves,
				mayOfferDraw);

		TurnAction action;
		try {
			action = strategy.onTurn(context);
		} catch (RuntimeException e) {
			return error(500, "strategy failed");
		}
		if (action == null) {
			return error(500, "strategy returned no action");
		}

		var body = new JsonObject();
		body.add("moves", GSON.toJsonTree(action.moves()));
		body.addProperty("offerDraw", action.offerDraw());
		return new Response(200, GSON.toJson(body));
	}

	private Response drawDecision(JsonObject envelope) {
		var parsed = parseDecisionState(envelope, false);
		var drawOffer = requiredObject(parsed.state(), "drawOffer");
		if (!requiredBoolean(drawOffer, "pending")) {
			throw new IllegalArgumentException("draw offer is not pending");
		}
		var context = new DrawDecisionContext(
				parsed.gameId(), parsed.seat(), parsed.version(), parsed.dfen(), parsed.clock());

		DrawAction action;
		try {
			action = strategy.onDrawDecision(context);
		} catch (RuntimeException e) {
			return error(500, "strategy failed");
		}
		if (action == null) {
			return error(500, "strategy returned no action");
		}

		var body = new JsonObject();
		body.addProperty("acceptDraw", action.acceptDraw());
		return new Response(200, GSON.toJson(body));
	}

	private DecisionState parseDecisionState(JsonObject envelope, boolean expectedDicePending) {
		var gameId = requiredString(envelope, "gameId");
		var seat = requiredSeat(envelope, "seat");
		var state = requiredObject(envelope, "state");
		var version = requiredLong(state, "version");
		var dfen = requiredString(state, "dfen");
		var activeSeat = requiredSeat(state, "activeSeat");
		var dicePending = requiredBoolean(state, "dicePending");
		if (!activeSeat.equals(seat) || dicePending != expectedDicePending) {
			throw new IllegalArgumentException("delivery state does not match its decision type");
		}
		return new DecisionState(gameId, seat, version, dfen, state, clock(state, seat));
	}

	private List<List<String>> legalMoves(String gameId, long version, String dfen, JsonObject state) {
		if (!state.has("legalMoves")) {
			return null;
		}
		var element = state.get("legalMoves");
		if (element.isJsonNull()) {
			return playApiBaseUrl == null ? null : fetchLegalMoves(gameId, version, dfen);
		}
		if (!element.isJsonObject()) {
			throw new IllegalArgumentException("legalMoves must be an object or null");
		}
		return flattenLegalMoves(element.getAsJsonObject());
	}

	private static GameClock clock(JsonObject state, String seat) {
		if (!state.has(FIELD_CLOCKS) || state.get(FIELD_CLOCKS).isJsonNull()) {
			return null;
		}
		var clocks = requiredObject(state, FIELD_CLOCKS);
		var white = requiredLong(clocks, "white");
		var black = requiredLong(clocks, "black");
		if (white < 0 || black < 0) {
			throw new IllegalArgumentException("clock values must not be negative");
		}
		var own = seat.equals("White") ? white : black;
		var opponent = seat.equals("White") ? black : white;
		return new GameClock(own, opponent, fischerIncrementMillis(state));
	}

	/** Fetches and flattens the fallback move tree; every failure degrades to {@code null}. */
	private List<List<String>> fetchLegalMoves(String gameId, long expectedVersion, String expectedDfen) {
		try {
			var uri = URI.create(playApiBaseUrl + "/games/" + gameId + "/moves");
			var request = HttpRequest.newBuilder(uri).timeout(FALLBACK_TIMEOUT).GET().build();
			var response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				return null;
			}
			var body = GSON.fromJson(response.body(), JsonObject.class);
			if (requiredLong(body, "version") != expectedVersion
					|| !requiredString(body, "dfen").equals(expectedDfen)
					|| !requiredBoolean(body, "dicePending")) {
				return null;
			}
			return flattenLegalMoves(requiredObject(body, "legalMoves"));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (Exception e) {
			return null;
		}
	}

	private static List<List<String>> flattenLegalMoves(JsonObject tree) {
		var paths = new ArrayList<List<String>>();
		for (var entry : tree.entrySet()) {
			if (!entry.getValue().isJsonObject()) {
				throw new IllegalArgumentException("legal move branches must be objects");
			}
			var move = entry.getKey();
			var subtree = entry.getValue().getAsJsonObject();
			if (subtree.entrySet().isEmpty()) {
				paths.add(List.of(move));
			} else {
				for (var continuation : flattenLegalMoves(subtree)) {
					var path = new ArrayList<String>(continuation.size() + 1);
					path.add(move);
					path.addAll(continuation);
					paths.add(path);
				}
			}
		}
		return paths;
	}

	private static Long fischerIncrementMillis(JsonObject state) {
		if (!state.has(FIELD_TIME_CONTROL) || !state.get(FIELD_TIME_CONTROL).isJsonObject()) {
			return null;
		}
		var timeControl = state.getAsJsonObject(FIELD_TIME_CONTROL);
		if (!timeControl.has(VARIANT_FISCHER) || !timeControl.get(VARIANT_FISCHER).isJsonObject()) {
			return null;
		}
		var increment = timeControl.getAsJsonObject(VARIANT_FISCHER).get("incrementSeconds");
		if (increment == null || !increment.isJsonPrimitive() || !increment.getAsJsonPrimitive().isNumber()) {
			return null;
		}
		try {
			var seconds = exactLong(increment);
			return seconds < 0 ? null : Math.multiplyExact(seconds, 1000L);
		} catch (ArithmeticException | IllegalArgumentException e) {
			return null;
		}
	}

	private static String requiredString(JsonObject object, String field) {
		var element = object.get(field);
		if (element == null
				|| !element.isJsonPrimitive()
				|| !element.getAsJsonPrimitive().isString()
				|| element.getAsString().isBlank()) {
			throw new IllegalArgumentException(field + " must be a non-blank string");
		}
		return element.getAsString();
	}

	private static String requiredSeat(JsonObject object, String field) {
		var seat = requiredString(object, field);
		if (!seat.equals("White") && !seat.equals("Black")) {
			throw new IllegalArgumentException(field + " must be White or Black");
		}
		return seat;
	}

	private static JsonObject requiredObject(JsonObject object, String field) {
		var element = object == null ? null : object.get(field);
		if (element == null || !element.isJsonObject()) {
			throw new IllegalArgumentException(field + " must be an object");
		}
		return element.getAsJsonObject();
	}

	private static long requiredLong(JsonObject object, String field) {
		var element = object.get(field);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException(field + " must be an integer");
		}
		try {
			return exactLong(element);
		} catch (ArithmeticException | NumberFormatException e) {
			throw new IllegalArgumentException(field + " must be an integer", e);
		}
	}

	private static long exactLong(JsonElement element) {
		return new BigDecimal(element.getAsString()).longValueExact();
	}

	private static boolean requiredBoolean(JsonObject object, String field) {
		var element = object.get(field);
		if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
			throw new IllegalArgumentException(field + " must be a boolean");
		}
		return element.getAsBoolean();
	}

	private static boolean optionalBooleanTrue(JsonObject object, String field) {
		var element = object.get(field);
		return element != null
				&& element.isJsonPrimitive()
				&& element.getAsJsonPrimitive().isBoolean()
				&& element.getAsBoolean();
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private static Map<String, String> lowercaseKeys(Map<String, String> headers) {
		var result = new HashMap<String, String>();
		headers.forEach((key, value) -> result.put(key.toLowerCase(Locale.ROOT), value));
		return result;
	}

	private static Response error(int status, String message) {
		var body = new JsonObject();
		body.addProperty("error", message);
		return new Response(status, GSON.toJson(body));
	}

	private record DecisionState(
			String gameId, String seat, long version, String dfen, JsonObject state, GameClock clock) {}
}
