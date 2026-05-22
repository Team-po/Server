package team.po.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(
	String apiKey,
	String baseUrl,
	String model,
	Duration connectTimeout,
	Duration readTimeout,
	int maxOutputTokens,
	double temperature
) {
}
