package com.fortemate.dicechess.runtime;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 signing and verification for the DiceChess webhook delivery protocol.
 *
 * <p>The signed payload is {@code timestamp + "." + body}, hex-encoded. A delivery is accepted
 * only if its timestamp falls within {@link #REPLAY_WINDOW_SECONDS} of the verifier's clock,
 * and the comparison is constant-time to avoid leaking the secret through timing.
 */
public final class Signatures {

	/** The signature is only accepted within this many seconds of the current time, either side. */
	public static final long REPLAY_WINDOW_SECONDS = 300;

	/** Domain separation prefix prepended to the raw body when computing a version-2 activation response proof. */
	public static final String ACTIVATION_PROOF_PREFIX = "dicechess-webhook-activate-v2\n";

	private static final String ALGORITHM = "HmacSHA256";

	private Signatures() {}

	/**
	 * Computes the hex-encoded HMAC-SHA256 signature for a delivery.
	 *
	 * @param secret the webhook secret issued by the platform
	 * @param timestampEpochSeconds the delivery timestamp, Unix epoch seconds
	 * @param body the raw request body, exactly as transmitted
	 * @return the lowercase hex signature
	 */
	public static String sign(String secret, long timestampEpochSeconds, String body) {
		try {
			var mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
			var payload = timestampEpochSeconds + "." + body;
			var raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(raw);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("HmacSHA256 must be available on every JDK", e);
		}
	}

	/**
	 * Computes the lowercase-hex HMAC-SHA256 response proof for a version-2 verification challenge.
	 *
	 * @param secret the pending webhook secret issued by the platform
	 * @param rawBody the exact raw request body received from the platform
	 * @return the lowercase hex proof string
	 */
	public static String activationProof(String secret, String rawBody) {
		Objects.requireNonNull(secret, "secret must not be null");
		Objects.requireNonNull(rawBody, "rawBody must not be null");
		try {
			var mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
			mac.update(ACTIVATION_PROOF_PREFIX.getBytes(StandardCharsets.UTF_8));
			var raw = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(raw);
		} catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException("HmacSHA256 must be available on every JDK", e);
		}
	}

	/**
	 * Verifies a delivery's signature and freshness against a single secret.
	 *
	 * @param secret the webhook secret issued by the platform
	 * @param timestampEpochSeconds the delivery's claimed timestamp, Unix epoch seconds
	 * @param body the raw request body, exactly as received
	 * @param signature the hex signature supplied with the delivery
	 * @param nowEpochSeconds the verifier's current time, Unix epoch seconds
	 * @return {@code true} if the timestamp is within {@link #REPLAY_WINDOW_SECONDS} and the
	 *     signature matches
	 */
	public static boolean verify(
			String secret, long timestampEpochSeconds, String body, String signature, long nowEpochSeconds) {
		long ageSeconds;
		try {
			ageSeconds = Math.subtractExact(nowEpochSeconds, timestampEpochSeconds);
		} catch (ArithmeticException _) {
			return false;
		}
		if (ageSeconds < -REPLAY_WINDOW_SECONDS || ageSeconds > REPLAY_WINDOW_SECONDS) {
			return false;
		}
		var expected = sign(secret, timestampEpochSeconds, body);
		return MessageDigest.isEqual(
				expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * Verifies a delivery's signature and freshness against configured active and/or pending keys.
	 *
	 * <p>When both keys are configured, both signatures are evaluated using constant-time comparisons
	 * without early return to eliminate timing oracles.
	 *
	 * @param keys the active and/or pending webhook keys
	 * @param timestampEpochSeconds the delivery's claimed timestamp, Unix epoch seconds
	 * @param body the raw request body, exactly as received
	 * @param signature the hex signature supplied with the delivery
	 * @param nowEpochSeconds the verifier's current time, Unix epoch seconds
	 * @return {@code true} if the timestamp is within {@link #REPLAY_WINDOW_SECONDS} and
	 *     either key matches the signature
	 */
	public static boolean verify(
			WebhookKeys keys, long timestampEpochSeconds, String body, String signature, long nowEpochSeconds) {
		Objects.requireNonNull(keys, "keys must not be null");
		Objects.requireNonNull(body, "body must not be null");
		Objects.requireNonNull(signature, "signature must not be null");

		long ageSeconds;
		try {
			ageSeconds = Math.subtractExact(nowEpochSeconds, timestampEpochSeconds);
		} catch (ArithmeticException _) {
			return false;
		}
		if (ageSeconds < -REPLAY_WINDOW_SECONDS || ageSeconds > REPLAY_WINDOW_SECONDS) {
			return false;
		}

		var signatureBytes = signature.getBytes(StandardCharsets.UTF_8);
		var match = false;
		if (keys.hasActive()) {
			var expectedActive = sign(keys.active(), timestampEpochSeconds, body);
			match |= MessageDigest.isEqual(expectedActive.getBytes(StandardCharsets.UTF_8), signatureBytes);
		}
		if (keys.hasPending()) {
			var expectedPending = sign(keys.pending(), timestampEpochSeconds, body);
			match |= MessageDigest.isEqual(expectedPending.getBytes(StandardCharsets.UTF_8), signatureBytes);
		}
		return match;
	}
}
