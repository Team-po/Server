package team.po.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class PasswordResetPropertiesTest {

	@Test
	void constructor_throwsWhenClientResetUrlIsBlank() {
		assertThatThrownBy(() -> new PasswordResetProperties(
			Duration.ofMinutes(30),
			Duration.ofMinutes(1),
			" ",
			"TeamPo 비밀번호 재설정"
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("password-reset.client-reset-url is required.");
	}

	@Test
	void constructor_throwsWhenTokenTtlIsNotPositive() {
		assertThatThrownBy(() -> new PasswordResetProperties(
			Duration.ZERO,
			Duration.ofMinutes(1),
			"https://team-po.cloud/password-reset",
			"TeamPo 비밀번호 재설정"
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("password-reset.token-ttl must be positive.");
	}

	@Test
	void constructor_throwsWhenRequestCooldownIsNotPositive() {
		assertThatThrownBy(() -> new PasswordResetProperties(
			Duration.ofMinutes(30),
			Duration.ofSeconds(-1),
			"https://team-po.cloud/password-reset",
			"TeamPo 비밀번호 재설정"
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("password-reset.request-cooldown must be positive.");
	}

	@Test
	void constructor_appliesOptionalDefaults() {
		PasswordResetProperties properties = new PasswordResetProperties(
			null,
			null,
			"https://team-po.cloud/password-reset",
			null
		);

		assertThat(properties.tokenTtl()).isEqualTo(Duration.ofMinutes(30));
		assertThat(properties.requestCooldown()).isEqualTo(Duration.ofMinutes(1));
		assertThat(properties.clientResetUrl()).isEqualTo("https://team-po.cloud/password-reset");
		assertThat(properties.emailSubject()).isEqualTo("TeamPo 비밀번호 재설정");
	}
}
