package team.po.config;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "password-reset")
public record PasswordResetProperties(
	Duration tokenTtl,
	Duration requestCooldown,
	String clientResetUrl,
	List<String> clientResetAllowedHosts,
	String emailSubject
) {
	private static final Duration DEFAULT_TOKEN_TTL = Duration.ofMinutes(30);
	private static final Duration DEFAULT_REQUEST_COOLDOWN = Duration.ofMinutes(1);
	private static final List<String> DEFAULT_CLIENT_RESET_ALLOWED_HOSTS = List.of("team-po.cloud", "www.team-po.cloud");
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
		clientResetAllowedHosts = normalizeAllowedHosts(clientResetAllowedHosts);
		if (emailSubject == null || emailSubject.isBlank()) {
			emailSubject = DEFAULT_EMAIL_SUBJECT;
		}
	}

	private static List<String> normalizeAllowedHosts(List<String> allowedHosts) {
		if (allowedHosts == null) {
			return DEFAULT_CLIENT_RESET_ALLOWED_HOSTS;
		}

		List<String> normalizedHosts = allowedHosts.stream()
			.filter(StringUtils::hasText)
			.map(host -> host.trim().toLowerCase(Locale.ROOT))
			.distinct()
			.toList();
		if (normalizedHosts.isEmpty()) {
			throw new IllegalStateException("password-reset.client-reset-allowed-hosts must not be empty.");
		}

		return normalizedHosts;
	}
}
