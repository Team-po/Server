package team.po.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class PasswordResetClientUrlValidatorTest {

	@Test
	void validateClientResetUrl_acceptsHttpsAllowedHostOutsideLocalProfiles() {
		PasswordResetClientUrlValidator validator = validator(
			"https://team-po.cloud/password-reset",
			List.of("team-po.cloud"),
			"production"
		);

		assertThatCode(validator::validateClientResetUrl).doesNotThrowAnyException();
	}

	@Test
	void validateClientResetUrl_rejectsHttpOutsideLocalProfiles() {
		PasswordResetClientUrlValidator validator = validator(
			"http://team-po.cloud/password-reset",
			List.of("team-po.cloud"),
			"production"
		);

		assertThatThrownBy(validator::validateClientResetUrl)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("password-reset.client-reset-url must use https outside local/test profiles.");
	}

	@Test
	void validateClientResetUrl_rejectsDisallowedHostOutsideLocalProfiles() {
		PasswordResetClientUrlValidator validator = validator(
			"https://evil.example/password-reset",
			List.of("team-po.cloud"),
			"production"
		);

		assertThatThrownBy(validator::validateClientResetUrl)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("password-reset.client-reset-url host must be allowed outside local/test profiles.");
	}

	@Test
	void validateClientResetUrl_allowsLocalhostForLocalProfile() {
		PasswordResetClientUrlValidator validator = validator(
			"http://localhost:5173/password-reset",
			List.of("team-po.cloud"),
			"local"
		);

		assertThatCode(validator::validateClientResetUrl).doesNotThrowAnyException();
	}

	@Test
	void validateClientResetUrl_allowsLocalhostForH2Profile() {
		PasswordResetClientUrlValidator validator = validator(
			"http://localhost:5173/password-reset",
			List.of("team-po.cloud"),
			"h2"
		);

		assertThatCode(validator::validateClientResetUrl).doesNotThrowAnyException();
	}

	private PasswordResetClientUrlValidator validator(
		String clientResetUrl,
		List<String> clientResetAllowedHosts,
		String activeProfile
	) {
		PasswordResetProperties properties = new PasswordResetProperties(
			Duration.ofMinutes(30),
			Duration.ofMinutes(1),
			clientResetUrl,
			clientResetAllowedHosts,
			"TeamPo 비밀번호 재설정"
		);
		MockEnvironment environment = new MockEnvironment();
		environment.setActiveProfiles(activeProfile);
		return new PasswordResetClientUrlValidator(properties, environment);
	}
}
