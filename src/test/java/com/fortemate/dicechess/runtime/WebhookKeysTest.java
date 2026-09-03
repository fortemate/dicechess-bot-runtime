package com.fortemate.dicechess.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WebhookKeysTest {

	private static final String ACTIVE = "active-secret-1234567890abcdef1234567890abcdef1234567890abcdef12345678";
	private static final String PENDING = "pending-secret-fedcba0987654321fedcba0987654321fedcba0987654321fedcba";

	@Test
	void activeOnlyConfiguresActiveKeyOnly() {
		var keys = WebhookKeys.activeOnly(ACTIVE);
		assertThat(keys.active()).isEqualTo(ACTIVE);
		assertThat(keys.pending()).isNull();
		assertThat(keys.hasActive()).isTrue();
		assertThat(keys.hasPending()).isFalse();
	}

	@Test
	void activeOnlyRejectsNullOrBlank() {
		assertThatThrownBy(() -> WebhookKeys.activeOnly(null))
				.isInstanceOf(NullPointerException.class)
				.hasMessage("active secret must not be null");

		assertThatThrownBy(() -> WebhookKeys.activeOnly("   "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("active secret must not be blank");
	}

	@Test
	void pendingOnlyConfiguresPendingKeyOnly() {
		var keys = WebhookKeys.pendingOnly(PENDING);
		assertThat(keys.active()).isNull();
		assertThat(keys.pending()).isEqualTo(PENDING);
		assertThat(keys.hasActive()).isFalse();
		assertThat(keys.hasPending()).isTrue();
	}

	@Test
	void pendingOnlyRejectsNullOrBlank() {
		assertThatThrownBy(() -> WebhookKeys.pendingOnly(null))
				.isInstanceOf(NullPointerException.class)
				.hasMessage("pending secret must not be null");

		assertThatThrownBy(() -> WebhookKeys.pendingOnly("   "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("pending secret must not be blank");
	}

	@Test
	void activeAndPendingConfiguresBothKeys() {
		var keys = WebhookKeys.activeAndPending(ACTIVE, PENDING);
		assertThat(keys.active()).isEqualTo(ACTIVE);
		assertThat(keys.pending()).isEqualTo(PENDING);
		assertThat(keys.hasActive()).isTrue();
		assertThat(keys.hasPending()).isTrue();
	}

	@Test
	void activeAndPendingRejectsNullOrBlankKeys() {
		assertThatThrownBy(() -> WebhookKeys.activeAndPending(null, PENDING))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> WebhookKeys.activeAndPending(ACTIVE, null))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> WebhookKeys.activeAndPending("  ", PENDING))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("active secret must not be blank");
		assertThatThrownBy(() -> WebhookKeys.activeAndPending(ACTIVE, "  "))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("pending secret must not be blank");
	}

	@Test
	void constructorRejectsNeitherKeyConfigured() {
		assertThatThrownBy(() -> new WebhookKeys(null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("at least one of active or pending secret must be configured");

		assertThatThrownBy(() -> WebhookKeys.of(null, null))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("at least one of active or pending secret must be configured");
	}

	@Test
	void fromEnvironmentResolvesBothOrSingleKeys() {
		var both = WebhookKeys.fromEnvironment(Map.of(
				WebhookKeys.ENV_ACTIVE_SECRET, ACTIVE,
				WebhookKeys.ENV_PENDING_SECRET, PENDING));
		assertThat(both.hasActive()).isTrue();
		assertThat(both.hasPending()).isTrue();

		var activeOnly = WebhookKeys.fromEnvironment(Map.of(
				WebhookKeys.ENV_ACTIVE_SECRET, ACTIVE));
		assertThat(activeOnly.hasActive()).isTrue();
		assertThat(activeOnly.hasPending()).isFalse();

		var pendingOnly = WebhookKeys.fromEnvironment(Map.of(
				WebhookKeys.ENV_PENDING_SECRET, PENDING));
		assertThat(pendingOnly.hasActive()).isFalse();
		assertThat(pendingOnly.hasPending()).isTrue();
	}

	@Test
	void fromEnvironmentFailsFastWhenNeitherIsConfiguredOrBothBlank() {
		assertThatThrownBy(() -> WebhookKeys.fromEnvironment(Map.of()))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("neither DICECHESS_WEBHOOK_SECRET nor DICECHESS_WEBHOOK_NEXT_SECRET is configured");

		assertThatThrownBy(() -> WebhookKeys.fromEnvironment(Map.of(
				WebhookKeys.ENV_ACTIVE_SECRET, "   ",
				WebhookKeys.ENV_PENDING_SECRET, "")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("neither DICECHESS_WEBHOOK_SECRET nor DICECHESS_WEBHOOK_NEXT_SECRET is configured");
	}

	@Test
	void toStringNeverExposesSecrets() {
		var keys = WebhookKeys.activeAndPending(ACTIVE, PENDING);
		var string = keys.toString();

		assertThat(string).isEqualTo("WebhookKeys[hasActive=true, hasPending=true]");
		assertThat(string).doesNotContain(ACTIVE);
		assertThat(string).doesNotContain(PENDING);
	}
}
