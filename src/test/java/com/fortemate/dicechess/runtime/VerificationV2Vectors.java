package com.fortemate.dicechess.runtime;

/**
 * Canonical test vectors for the DiceChess version-2 webhook verification challenge (ADR 004).
 *
 * <p>These vectors define the exact test secret, timestamp, raw request bytes, request signature,
 * and response proof used across Fortemate repositories to ensure wire and cryptographic compatibility
 * before UI and backend feature enablement.
 */
public final class VerificationV2Vectors {

	/**
	 * Canonical 64-character test secret. Used as UTF-8 key bytes for HMAC-SHA256, NOT hex-decoded.
	 */
	public static final String CANONICAL_SECRET =
			"0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

	/** Canonical delivery timestamp in Unix epoch seconds. */
	public static final long CANONICAL_TIMESTAMP = 1788264000L;

	/**
	 * Canonical raw JSON request body for version-2 verification.
	 * No trailing newline, exact UTF-8 bytes.
	 */
	public static final String CANONICAL_RAW_BODY =
			"{\"type\":\"verification\",\"version\":2,\"bot\":{\"team\":\"acme\",\"name\":\"greedy\"},\"setupId\":\"whs_test\",\"revision\":\"whrev_test\",\"nonce\":\"AQIDBAUGBwgJCgsMDQ4PEA\"}";

	/**
	 * Canonical incoming X-DiceChess-Signature header value.
	 * Lowercase hex HMAC-SHA256 over {@code timestamp + "." + rawBody}.
	 */
	public static final String CANONICAL_REQUEST_SIGNATURE =
			"d5a25685fcf630eec4de6a07536aea2d302c0048c3fafd0a5f0b8dd0b908b451";

	/**
	 * Canonical outgoing response proof.
	 * Lowercase hex HMAC-SHA256 over {@code "dicechess-webhook-activate-v2\n" + rawBody}.
	 */
	public static final String CANONICAL_RESPONSE_PROOF =
			"d5cb5c588d0bee2d87a2980061feb30e3e1d22cb6701c01934fdca35408090fa";

	/** Expected canonical 200 success response JSON body. */
	public static final String CANONICAL_SUCCESS_RESPONSE_BODY =
			"{\"nonce\":\"AQIDBAUGBwgJCgsMDQ4PEA\",\"proof\":\"d5cb5c588d0bee2d87a2980061feb30e3e1d22cb6701c01934fdca35408090fa\"}";

	/** Canonical nonce carrying exactly 128 random bits encoded as unpadded base64url. */
	public static final String CANONICAL_NONCE = "AQIDBAUGBwgJCgsMDQ4PEA";

	private VerificationV2Vectors() {}
}
