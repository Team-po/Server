package team.po.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github.app")
public record GithubAppProperties(
	Long id,
	String slug,
	String privateKey,
	Duration installationStateTtl,
	String apiBaseUrl
) {
	private static final Duration DEFAULT_INSTALLATION_STATE_TTL = Duration.ofMinutes(5);
	private static final String DEFAULT_API_BASE_URL = "https://api.github.com";

	public GithubAppProperties {
		if (slug == null) {
			slug = "";
		}
		if (privateKey == null) {
			privateKey = "";
		}
		if (installationStateTtl == null) {
			installationStateTtl = DEFAULT_INSTALLATION_STATE_TTL;
		}
		if (apiBaseUrl == null || apiBaseUrl.isBlank()) {
			apiBaseUrl = DEFAULT_API_BASE_URL;
		}
	}
}
