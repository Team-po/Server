package team.po.feature.devguide.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gemini")
public record GeminiProperties(String apiKey, String baseUrl, String model, int timeoutSeconds, int maxOutputTokens,
							   double temperature) {
}