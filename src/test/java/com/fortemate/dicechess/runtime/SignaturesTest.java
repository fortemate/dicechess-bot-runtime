package com.fortemate.dicechess.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The signature vector here is the ecosystem-wide one: the same bytes are asserted in play-api's
 * WebhookSecuritySuite and the TypeScript/Python starters — every implementation provably speaks
 * one scheme.
 */
class SignaturesTest {

	private static final String SECRET = "test-webhook-secret";
	private static final long NOW = 1752750000L;

	@Test
	void signMatchesTheEcosystemWideVector() {
		assertThat(Signatures.sign(SECRET, NOW, "{\"hello\":true}"))
				.isEqualTo("5f4fbf105bab278dc6205788389e09884bd554b1f866ca11ccc9ce97ddd9b3f6");
	}

	@Test
	void verifyAcceptsAGenuineFreshSignature() {
		var body = "{\"type\":\"yourTurn\"}";
		var signature = Signatures.sign(SECRET, NOW, body);
		assertThat(Signatures.verify(SECRET, NOW, body, signature, NOW)).isTrue();
	}

	@Test
	void verifyRejectsAWrongSecret() {
		var body = "{\"type\":\"yourTurn\"}";
		var signature = Signatures.sign("some-other-secret", NOW, body);
		assertThat(Signatures.verify(SECRET, NOW, body, signature, NOW)).isFalse();
	}

	@Test
	void verifyRejectsATamperedSignature() {
		var body = "{\"type\":\"yourTurn\"}";
		assertThat(Signatures.verify(SECRET, NOW, body, "deadbeef", NOW)).isFalse();
	}

	@Test
	void verifyRejectsAStaleTimestampEvenWithAGenuineSignature() {
		var body = "{\"type\":\"yourTurn\"}";
		var staleTimestamp = NOW - 3600;
		var signature = Signatures.sign(SECRET, staleTimestamp, body);
		assertThat(Signatures.verify(SECRET, staleTimestamp, body, signature, NOW)).isFalse();
	}

	@Test
	void verifyAcceptsExactlyTheEdgeOfTheReplayWindow() {
		var body = "{\"type\":\"yourTurn\"}";
		var timestamp = NOW - Signatures.REPLAY_WINDOW_SECONDS;
		var signature = Signatures.sign(SECRET, timestamp, body);
		assertThat(Signatures.verify(SECRET, timestamp, body, signature, NOW)).isTrue();
	}

	@Test
	void verifyRejectsTimestampDifferencesThatOverflowALong() {
		var body = "{\"type\":\"yourTurn\"}";
		var minSignature = Signatures.sign(SECRET, Long.MIN_VALUE, body);
		var maxSignature = Signatures.sign(SECRET, Long.MAX_VALUE, body);

		assertThat(Signatures.verify(SECRET, Long.MIN_VALUE, body, minSignature, 0)).isFalse();
		assertThat(Signatures.verify(SECRET, Long.MAX_VALUE, body, maxSignature, -1)).isFalse();
	}

	@Test
	void canonicalVerificationV2RequestSignatureAndProofMatchTheContractVector() {
		var signature = Signatures.sign(
				VerificationV2Vectors.CANONICAL_SECRET,
				VerificationV2Vectors.CANONICAL_TIMESTAMP,
				VerificationV2Vectors.CANONICAL_RAW_BODY);
		assertThat(signature).isEqualTo(VerificationV2Vectors.CANONICAL_REQUEST_SIGNATURE);

		var proof = Signatures.activationProof(
				VerificationV2Vectors.CANONICAL_SECRET,
				VerificationV2Vectors.CANONICAL_RAW_BODY);
		assertThat(proof).isEqualTo(VerificationV2Vectors.CANONICAL_RESPONSE_PROOF);
	}

	@Test
	void multiKeyVerifyAcceptsDeliveriesSignedWithActiveOrPendingKey() {
		var activeSecret = "active-key-secret-1234567890abcdef1234567890abcdef1234567890abcdef12";
		var pendingSecret = "pending-key-secret-abcdef1234567890abcdef1234567890abcdef1234567890";
		var dualKeys = WebhookKeys.activeAndPending(activeSecret, pendingSecret);
		var body = "{\"type\":\"yourTurn\"}";

		var activeSig = Signatures.sign(activeSecret, NOW, body);
		var pendingSig = Signatures.sign(pendingSecret, NOW, body);
		var wrongSig = Signatures.sign("wrong-secret", NOW, body);

		assertThat(Signatures.verify(dualKeys, NOW, body, activeSig, NOW)).isTrue();
		assertThat(Signatures.verify(dualKeys, NOW, body, pendingSig, NOW)).isTrue();
		assertThat(Signatures.verify(dualKeys, NOW, body, wrongSig, NOW)).isFalse();
	}

	@Test
	void multiKeyVerifyWorksForActiveOnlyAndPendingOnlyConfigurations() {
		var activeKeys = WebhookKeys.activeOnly(SECRET);
		var pendingKeys = WebhookKeys.pendingOnly(SECRET);
		var body = "{\"type\":\"yourTurn\"}";
		var signature = Signatures.sign(SECRET, NOW, body);

		assertThat(Signatures.verify(activeKeys, NOW, body, signature, NOW)).isTrue();
		assertThat(Signatures.verify(pendingKeys, NOW, body, signature, NOW)).isTrue();
	}

	@Test
	void multiKeyVerifyRejectsStaleTimestampEvenWithDualKeys() {
		var keys = WebhookKeys.activeAndPending(SECRET, "pending-secret");
		var body = "{\"type\":\"yourTurn\"}";
		var stale = NOW - Signatures.REPLAY_WINDOW_SECONDS - 1;
		var signature = Signatures.sign(SECRET, stale, body);

		assertThat(Signatures.verify(keys, stale, body, signature, NOW)).isFalse();
	}
}
