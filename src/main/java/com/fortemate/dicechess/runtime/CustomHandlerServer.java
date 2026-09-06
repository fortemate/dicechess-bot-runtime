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
	private static final String DEFAULT_PLAY_API_BASE_URL = "https://play-api.fortemate.com";
	private static final long DEFAULT_DRAIN_DEADLINE_SECONDS = 30L;

	private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

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

		attachShutdownHook(server, isDraining, token, baseUrl, drainDeadline, true);
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
		server.createContext(path, exchange -> {
			try (exchange) {
				var headers = new HashMap<String, String>();
				exchange.getRequestHeaders().forEach((name, values) -> {
					if (!values.isEmpty()) {
						headers.put(name, values.getFirst());
					}
				});
				var rawBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

				WebhookHandler activeHandler;
				if (isDraining.get()) {
					activeHandler = new WebhookHandler(
							handler.keys(), null, context -> TurnAction.resign(), () -> true);
				} else {
					activeHandler = handler;
				}

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
	 * Executes the shutdown procedure.
	 *
	 * <p>When {@code token} is present (non-null and non-blank), executes {@code POST /bot/games/resign-all}
	 * with {@code {"pauseSeating": true}} and stops the server.
	 * Without a token, enters drain mode (where incoming deliveries answer with {@code resign: true}) until
	 * {@code drainDeadlineSeconds} expires, then stops the server.
	 *
	 * @param server the server to stop
	 * @param isDraining the flag controlling drain mode
	 * @param token the bot token, or {@code null}/blank if unconfigured
	 * @param baseUrl play-api base URL
	 * @param drainDeadlineSeconds drain mode timeout in seconds
	 * @param exitAfterShutdown whether to call {@code System.exit(0)} after completion
	 */
	public static void executeShutdown(
			HttpServer server,
			AtomicBoolean isDraining,
			String token,
			String baseUrl,
			long drainDeadlineSeconds,
			boolean exitAfterShutdown) {
		if (token != null && !token.isBlank()) {
			try {
				var normalizedUrl = stripTrailingSlash(baseUrl);
				var uri = URI.create(normalizedUrl + "/bot/games/resign-all");
				var body = "{\"pauseSeating\":true}";
				var request = HttpRequest.newBuilder(uri)
						.header("Authorization", "Bearer " + token)
						.header("Content-Type", "application/json")
						.timeout(Duration.ofSeconds(10))
						.POST(HttpRequest.BodyPublishers.ofString(body))
						.build();
				HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.discarding());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception _) {
				// Proceed to stop server
			}
			server.stop(0);
		} else {
			if (isDraining != null) {
				isDraining.set(true);
			}
			try {
				Thread.sleep(Math.max(0L, drainDeadlineSeconds * 1000L));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			server.stop(0);
		}

		if (exitAfterShutdown) {
			System.exit(0);
		}
	}

	/**
	 * Attaches a JVM shutdown hook for SIGTERM.
	 *
	 * @param server the server to stop
	 * @param isDraining the flag controlling drain mode
	 * @param token the bot token, or {@code null}/blank if unconfigured
	 * @param baseUrl play-api base URL
	 * @param drainDeadlineSeconds drain mode timeout in seconds
	 * @param exitAfterShutdown whether to call {@code System.exit(0)} after completion
	 * @return the attached shutdown hook thread
	 */
	public static Thread attachShutdownHook(
			HttpServer server,
			AtomicBoolean isDraining,
			String token,
			String baseUrl,
			long drainDeadlineSeconds,
			boolean exitAfterShutdown) {
		var hook = new Thread(
				() -> executeShutdown(server, isDraining, token, baseUrl, drainDeadlineSeconds, exitAfterShutdown));
		Runtime.getRuntime().addShutdownHook(hook);
		return hook;
	}

	private static String stripTrailingSlash(String url) {
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}
}
