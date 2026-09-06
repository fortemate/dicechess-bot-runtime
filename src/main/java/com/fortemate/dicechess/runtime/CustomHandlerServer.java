package com.fortemate.dicechess.runtime;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A minimal HTTP server for the Azure Functions custom-handler model: one path, one {@link
 * WebhookHandler}, no framework.
 *
 * <p>Azure starts the custom-handler process and tells it which port to listen on via the
 * {@code FUNCTIONS_CUSTOMHANDLER_PORT} environment variable; {@link #startFromEnvironment}
 * reads it. Nothing here is Azure-specific beyond that one variable — the same server runs
 * identically under {@code func start} locally, or under any other host that can point HTTP
 * traffic at a port.
 */
public final class CustomHandlerServer {

	/** Environment variable name holding the bot bearer token. */
	public static final String ENV_BOT_TOKEN = "DICECHESS_BOT_TOKEN";

	/** Environment variable name holding play-api's base URL. */
	public static final String ENV_PLAY_API_BASE_URL = "DICECHESS_PLAY_API_BASE_URL";

	/** Environment variable name holding the drain deadline in seconds. */
	public static final String ENV_DRAIN_DEADLINE_SECONDS = "DICECHESS_DRAIN_DEADLINE_SECONDS";

	private static final String DEFAULT_PATH = "/api/webhook";
	private static final int DEFAULT_PORT = 8080;
	private static final String DEFAULT_PLAY_API_BASE_URL = "https://api.fortemate.com";

	/**
	 * How long drain mode keeps answering deliveries with a resignation before the server stops.
	 *
	 * <p>Kept below the stop grace a container runtime allows by default (Docker sends SIGKILL ten seconds after
	 * SIGTERM): a longer deadline would simply be cut short, turning a clean shutdown into a killed one. Raise it only
	 * together with the platform's own grace period.
	 */
	private static final long DEFAULT_DRAIN_DEADLINE_SECONDS = 8L;

	private static final Duration RESIGN_ALL_TIMEOUT = Duration.ofSeconds(5);

	private CustomHandlerServer() {}

	/**
	 * Starts the server on the port named by {@code FUNCTIONS_CUSTOMHANDLER_PORT} (default
	 * {@code 8080}), serving {@code handler} at {@code /api/webhook}, and attaches a SIGTERM
	 * shutdown hook using environment variables.
	 *
	 * @param handler the webhook logic to serve
	 * @return the running server; call {@link HttpServer#stop} to shut it down
	 * @throws IOException if the port cannot be bound
	 */
	public static HttpServer startFromEnvironment(WebhookHandler handler) throws IOException {
		var port = Integer.parseInt(
				System.getenv().getOrDefault("FUNCTIONS_CUSTOMHANDLER_PORT", String.valueOf(DEFAULT_PORT)));
		var isDraining = new AtomicBoolean(false);
		var server = start(port, DEFAULT_PATH, handler, isDraining);

		var env = System.getenv();
		var token = env.get(ENV_BOT_TOKEN);
		var baseUrl = env.getOrDefault(ENV_PLAY_API_BASE_URL, DEFAULT_PLAY_API_BASE_URL);
		long drainDeadline = DEFAULT_DRAIN_DEADLINE_SECONDS;
		if (env.containsKey(ENV_DRAIN_DEADLINE_SECONDS)) {
			try {
				drainDeadline = Long.parseLong(env.get(ENV_DRAIN_DEADLINE_SECONDS));
			} catch (NumberFormatException _) {
				// keep default
			}
		}

		attachShutdownHook(server, isDraining, token, baseUrl, drainDeadline);
		return server;
	}

	/**
	 * Starts the server on an explicit port and path.
	 *
	 * @param port the port to listen on
	 * @param path the path {@code handler} answers on
	 * @param handler the webhook logic to serve
	 * @return the running server; call {@link HttpServer#stop} to shut it down
	 * @throws IOException if the port cannot be bound
	 */
	public static HttpServer start(int port, String path, WebhookHandler handler) throws IOException {
		return start(port, path, handler, new AtomicBoolean(false));
	}

	/**
	 * Starts the server on an explicit port and path with a shared drain flag.
	 *
	 * @param port the port to listen on
	 * @param path the path {@code handler} answers on
	 * @param handler the webhook logic to serve
	 * @param isDraining the flag indicating whether drain mode is active
	 * @return the running server; call {@link HttpServer#stop} to shut it down
	 * @throws IOException if the port cannot be bound
	 */
	public static HttpServer start(
			int port, String path, WebhookHandler handler, AtomicBoolean isDraining)
			throws IOException {
		Objects.requireNonNull(handler, "handler must not be null");
		Objects.requireNonNull(isDraining, "isDraining must not be null");

		var server = HttpServer.create(new InetSocketAddress(port), 0);
		// Derived once, not per delivery: the same keys, base URL and strategy, with a policy that resigns before
		// dispatch. Rebuilding it per request would allocate needlessly and silently drop whatever configuration the
		// application gave the original handler.
		var drainHandler = new WebhookHandler(handler, () -> true);
		server.createContext(path, exchange -> {
			try (exchange) {
				var headers = new HashMap<String, String>();
				exchange.getRequestHeaders().forEach((name, values) -> {
					if (!values.isEmpty()) {
						headers.put(name, values.getFirst());
					}
				});
				var rawBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

				var activeHandler = isDraining.get() ? drainHandler : handler;
				var response = activeHandler.handle(headers, rawBody, Instant.now().getEpochSecond());

				var bytes = response.jsonBody().getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", "application/json");
				exchange.sendResponseHeaders(response.status(), bytes.length);
				try (var out = exchange.getResponseBody()) {
					out.write(bytes);
				}
			}
		});
		server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
		server.start();
		return server;
	}

	/**
	 * Executes the shutdown procedure and reports which path was taken.
	 *
	 * <p>With a token, {@code POST /bot/games/resign-all} concedes every game the bot is seated in and pauses its
	 * seating, which is the only mechanism that reaches a game whose turn is not currently the bot's. A refused or
	 * unreachable call falls through to drain mode rather than exiting as if the games had been conceded: an expired
	 * token answers {@code 401}, and silently treating that as success leaves the bot's games running until they flag.
	 *
	 * <p>Drain mode answers every further delivery with a resignation until {@code drainDeadlineSeconds} elapses. It is
	 * best-effort by nature: a game only receives a delivery when it is the bot's turn.
	 *
	 * <p>This method never calls {@code System.exit}. It is invoked from a shutdown hook, and {@code Runtime.exit}
	 * called while the shutdown sequence is already running blocks that thread indefinitely, so the JVM would hang
	 * until the orchestrator's SIGKILL instead of stopping cleanly.
	 *
	 * @param server the server to stop
	 * @param isDraining the flag controlling drain mode
	 * @param token the bot token, or {@code null}/blank if unconfigured
	 * @param baseUrl play-api base URL
	 * @param drainDeadlineSeconds drain mode timeout in seconds
	 * @return which path the shutdown actually took
	 */
	public static ShutdownOutcome executeShutdown(
			HttpServer server,
			AtomicBoolean isDraining,
			String token,
			String baseUrl,
			long drainDeadlineSeconds) {
		Objects.requireNonNull(server, "server must not be null");

		if (token != null && !token.isBlank() && resignAll(token, baseUrl)) {
			server.stop(0);
			return ShutdownOutcome.RESIGNED_ALL;
		}

		if (isDraining != null) {
			isDraining.set(true);
		}
		try {
			Thread.sleep(Math.max(0L, drainDeadlineSeconds * 1000L));
		} catch (InterruptedException _) {
			Thread.currentThread().interrupt();
		}
		server.stop(0);
		return ShutdownOutcome.DRAINED;
	}

	/**
	 * Concedes every game through play-api. Returns whether the server actually accepted the request; anything else,
	 * including a transport failure, is reported on {@code System.err} so an operator can tell a conceded shutdown
	 * from one that only looked like it.
	 */
	private static boolean resignAll(String token, String baseUrl) {
		var uri = URI.create(stripTrailingSlash(baseUrl) + "/bot/games/resign-all");
		var request = HttpRequest.newBuilder(uri)
				.header("Authorization", "Bearer " + token)
				.header("Content-Type", "application/json")
				.timeout(RESIGN_ALL_TIMEOUT)
				.POST(HttpRequest.BodyPublishers.ofString("{\"pauseSeating\":true}"))
				.build();
		try (var client = HttpClient.newHttpClient()) {
			var status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
			if (status >= 200 && status < 300) {
				return true;
			}
			System.err.printf("[dicechess] resign-all answered HTTP %d; draining instead%n", status);
		} catch (InterruptedException _) {
			Thread.currentThread().interrupt();
			System.err.println("[dicechess] resign-all was interrupted; draining instead");
		} catch (IOException | RuntimeException e) {
			System.err.printf("[dicechess] resign-all failed (%s); draining instead%n", e);
		}
		return false;
	}

	/**
	 * Attaches a JVM shutdown hook that runs {@link #executeShutdown} on SIGTERM.
	 *
	 * @param server the server to stop
	 * @param isDraining the flag controlling drain mode
	 * @param token the bot token, or {@code null}/blank if unconfigured
	 * @param baseUrl play-api base URL
	 * @param drainDeadlineSeconds drain mode timeout in seconds
	 * @return the attached shutdown hook thread
	 */
	public static Thread attachShutdownHook(
			HttpServer server,
			AtomicBoolean isDraining,
			String token,
			String baseUrl,
			long drainDeadlineSeconds) {
		var hook = new Thread(() -> executeShutdown(server, isDraining, token, baseUrl, drainDeadlineSeconds));
		Runtime.getRuntime().addShutdownHook(hook);
		return hook;
	}

	/** Which path {@link #executeShutdown} took. */
	public enum ShutdownOutcome {

		/** play-api accepted {@code POST /bot/games/resign-all}; every game was conceded. */
		RESIGNED_ALL,

		/** No token, or the call did not succeed: further deliveries were answered with a resignation instead. */
		DRAINED
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
