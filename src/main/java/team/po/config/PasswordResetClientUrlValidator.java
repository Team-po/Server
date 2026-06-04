package team.po.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

@Component
public class PasswordResetClientUrlValidator {

	private final PasswordResetProperties passwordResetProperties;
	private final Environment environment;

	public PasswordResetClientUrlValidator(
		PasswordResetProperties passwordResetProperties,
		Environment environment
	) {
		this.passwordResetProperties = passwordResetProperties;
		this.environment = environment;
	}

	@PostConstruct
	void validateClientResetUrl() {
		if (acceptsInsecureClientResetUrl()) {
			return;
		}

		URI clientResetUri = parseClientResetUri(passwordResetProperties.clientResetUrl());
		if (!"https".equalsIgnoreCase(clientResetUri.getScheme())) {
			throw new IllegalStateException(
				"password-reset.client-reset-url must use https outside local/test profiles."
			);
		}

		String clientResetHost = clientResetUri.getHost();
		if (!StringUtils.hasText(clientResetHost)) {
			throw new IllegalStateException("password-reset.client-reset-url must include a host.");
		}

		Set<String> allowedHosts = passwordResetProperties.clientResetAllowedHosts().stream()
			.map(host -> host.toLowerCase(Locale.ROOT))
			.collect(Collectors.toUnmodifiableSet());
		if (!allowedHosts.contains(clientResetHost.toLowerCase(Locale.ROOT))) {
			throw new IllegalStateException(
				"password-reset.client-reset-url host must be allowed outside local/test profiles."
			);
		}
	}

	private boolean acceptsInsecureClientResetUrl() {
		return environment.acceptsProfiles(Profiles.of("local", "test", "h2", "flyway"));
	}

	private URI parseClientResetUri(String clientResetUrl) {
		try {
			return new URI(clientResetUrl);
		} catch (URISyntaxException exception) {
			throw new IllegalStateException("password-reset.client-reset-url must be a valid URI.", exception);
		}
	}
}
