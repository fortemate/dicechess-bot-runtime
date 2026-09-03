package com.fortemate.dicechess.runtime;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable configuration for active and pending webhook secrets.
 *
 * <p>Supports three valid states:
 * <ul>
 *   <li><b>Active only:</b> normal steady-state operation.</li>
 *   <li><b>Active and pending:</b> zero-downtime rotation cutover during which verification challenges
 *       are verified against the pending key, while gameplay deliveries accept either key.</li>
 *   <li><b>Pending only:</b> initial registration of a new bot before promotion to active.</li>
 * </ul>
 *
 * <p>Secrets are held in their issued representation (e.g. 64 lowercase hex characters) and used
 * directly as UTF-8 bytes for HMAC-SHA256 operations without hex decoding.
 *
 * <p>To prevent credential leakage, {@link #toString()} never renders secret key values.
 *
 * @param active the currently active webhook secret, or {@code null} if pending-only
 * @param pending the candidate/pending webhook secret, or {@code null} if active-only
 */
public record WebhookKeys(String active, String pending) {

	/** Standard environment variable name for the active webhook secret. */
	public static final String ENV_ACTIVE_SECRET = "DICECHESS_WEBHOOK_SECRET";

	/** Standard environment variable name for the pending/candidate webhook secret. */
	public static final String ENV_PENDING_SECRET = "DICECHESS_WEBHOOK_NEXT_SECRET";

	/**
	 * Creates and validates an immutable webhook key configuration.
	 *
	 * @param active the currently active webhook secret, or {@code null}
	 * @param pending the candidate/pending webhook secret, or {@code null}
	 * @throws IllegalArgumentException if neither key is configured, or if any configured key is blank
	 */
	public WebhookKeys {
		if (active != null && active.isBlank()) {
			throw new IllegalArgumentException("active secret must not be blank");
		}
		if (pending != null && pending.isBlank()) {
			throw new IllegalArgumentException("pending secret must not be blank");
		}
		if (active == null && pending == null) {
			throw new IllegalArgumentException("at least one of active or pending secret must be configured");
		}
	}

	/**
	 * Creates a configuration containing only an active key.
	 *
	 * @param active the active webhook secret
	 * @return the key configuration
	 * @throws NullPointerException if {@code active} is {@code null}
	 * @throws IllegalArgumentException if {@code active} is blank
	 */
	public static WebhookKeys activeOnly(String active) {
		Objects.requireNonNull(active, "active secret must not be null");
		return new WebhookKeys(active, null);
	}

	/**
	 * Creates a configuration containing only a pending key.
	 *
	 * @param pending the pending webhook secret
	 * @return the key configuration
	 * @throws NullPointerException if {@code pending} is {@code null}
	 * @throws IllegalArgumentException if {@code pending} is blank
	 */
	public static WebhookKeys pendingOnly(String pending) {
		Objects.requireNonNull(pending, "pending secret must not be null");
		return new WebhookKeys(null, pending);
	}

	/**
	 * Creates a configuration containing both an active and a pending key.
	 *
	 * @param active the active webhook secret
	 * @param pending the pending webhook secret
	 * @return the key configuration
	 * @throws NullPointerException if either key is {@code null}
	 * @throws IllegalArgumentException if either key is blank
	 */
	public static WebhookKeys activeAndPending(String active, String pending) {
		Objects.requireNonNull(active, "active secret must not be null");
		Objects.requireNonNull(pending, "pending secret must not be null");
		return new WebhookKeys(active, pending);
	}

	/**
	 * Convenience factory for creating a configuration with optional active and pending keys.
	 *
	 * @param active the active secret, or {@code null}
	 * @param pending the pending secret, or {@code null}
	 * @return the key configuration
	 * @throws IllegalArgumentException if neither key is configured, or if any configured key is blank
	 */
	public static WebhookKeys of(String active, String pending) {
		return new WebhookKeys(active, pending);
	}

	/**
	 * Creates a key configuration from the default system environment variables
	 * ({@value #ENV_ACTIVE_SECRET} and {@value #ENV_PENDING_SECRET}).
	 *
	 * @return the key configuration resolved from environment variables
	 * @throws IllegalArgumentException if neither environment variable is configured or non-blank
	 */
	public static WebhookKeys fromEnvironment() {
		return fromEnvironment(System.getenv());
	}

	/**
	 * Creates a key configuration from a specified environment map.
	 *
	 * @param env the environment mapping to read keys from
	 * @return the key configuration
	 * @throws NullPointerException if {@code env} is {@code null}
	 * @throws IllegalArgumentException if neither environment variable is configured or non-blank
	 */
	public static WebhookKeys fromEnvironment(Map<String, String> env) {
		Objects.requireNonNull(env, "environment map must not be null");
		var active = normalize(env.get(ENV_ACTIVE_SECRET));
		var pending = normalize(env.get(ENV_PENDING_SECRET));
		if (active == null && pending == null) {
			throw new IllegalArgumentException(
					"neither " + ENV_ACTIVE_SECRET + " nor " + ENV_PENDING_SECRET + " is configured");
		}
		return new WebhookKeys(active, pending);
	}

	/**
	 * Returns whether an active key is configured.
	 *
	 * @return {@code true} if active key is present
	 */
	public boolean hasActive() {
		return active != null;
	}

	/**
	 * Returns whether a pending key is configured.
	 *
	 * @return {@code true} if pending key is present
	 */
	public boolean hasPending() {
		return pending != null;
	}

	@Override
	public String toString() {
		return "WebhookKeys[hasActive=" + hasActive() + ", hasPending=" + hasPending() + "]";
	}

	private static String normalize(String value) {
		return value == null || value.isBlank() ? null : value;
	}
}
