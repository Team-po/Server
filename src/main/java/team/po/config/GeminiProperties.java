package team.po.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(String apiKey, String baseUrl, String model, int connectTimeoutSeconds,
							   int readTimeoutSeconds, int maxOutputTokens,
							   double temperature) {
}