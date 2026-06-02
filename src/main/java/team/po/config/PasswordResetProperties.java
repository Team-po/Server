package team.po.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "password-reset")
public record PasswordResetProperties(
	Duration tokenTtl,
	Duration requestCooldown,
	String clientResetUrl,
	String emailSubject
) {
	private static final Duration DEFAULT_TOKEN_TTL = Duration.ofMinutes(30);
	private static final Duration DEFAULT_REQUEST_COOLDOWN = Duration.ofMinutes(1);
	private static final String DEFAULT_EMAIL_SUBJECT = "TeamPo 비밀번호 재설정";

	public PasswordResetProperties {
		if (tokenTtl == null) {
			tokenTtl = DEFAULT_TOKEN_TTL;
		}
		if (requestCooldown == null) {
			requestCooldown = DEFAULT_REQUEST_COOLDOWN;
		}
		if (!tokenTtl.isPositive()) {
			throw new IllegalStateException("password-reset.token-ttl must be positive.");
		}
		if (!requestCooldown.isPositive()) {
			throw new IllegalStateException("password-reset.request-cooldown must be positive.");
		}
		if (clientResetUrl == null || clientResetUrl.isBlank()) {
			throw new IllegalStateException("password-reset.client-reset-url is required.");
		}
		if (emailSubject == null || emailSubject.isBlank()) {
			emailSubject = DEFAULT_EMAIL_SUBJECT;
		}
	}
}
