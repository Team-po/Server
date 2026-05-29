package team.po.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class EmailAuthPropertiesTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
		.withUserConfiguration(TestConfig.class);

	@Test
	void bindsEmailAuthProperties() {
		contextRunner
			.withPropertyValues(
				"spring.mail.username=no-reply@teampo.com",
				"spring.mail.auth-code-ttl=PT3M",
				"spring.mail.verified-ttl=PT15M",
				"spring.mail.auth-code-subject=인증번호 안내"
			)
			.run(context -> {
				EmailAuthProperties properties = context.getBean(EmailAuthProperties.class);

				assertThat(properties.username()).isEqualTo("no-reply@teampo.com");
				assertThat(properties.authCodeTtl()).isEqualTo(Duration.ofMinutes(3));
				assertThat(properties.verifiedTtl()).isEqualTo(Duration.ofMinutes(15));
				assertThat(properties.authCodeSubject()).isEqualTo("인증번호 안내");
			});
	}

	@Test
	void usesDefaultsWhenPropertiesAreMissing() {
		contextRunner.run(context -> {
			EmailAuthProperties properties = context.getBean(EmailAuthProperties.class);

			assertThat(properties.username()).isEmpty();
			assertThat(properties.authCodeTtl()).isEqualTo(Duration.ofMinutes(5));
			assertThat(properties.verifiedTtl()).isEqualTo(Duration.ofMinutes(10));
			assertThat(properties.authCodeSubject()).isEqualTo("TeamPo 이메일 인증번호");
		});
	}

	@Configuration
	@EnableConfigurationProperties(EmailAuthProperties.class)
	static class TestConfig {
	}
}
