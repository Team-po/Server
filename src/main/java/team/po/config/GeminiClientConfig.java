package team.po.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(GeminiProperties.class)
public class GeminiClientConfig {

	private final GeminiProperties geminiProperties;

	@Bean
	@Qualifier("geminiRestClient")
	public RestClient geminiRestClient() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(geminiProperties.connectTimeout());
		requestFactory.setReadTimeout(geminiProperties.readTimeout());

		return RestClient.builder()
			.baseUrl(geminiProperties.baseUrl())
			.defaultHeader("x-goog-api-key", geminiProperties.apiKey())
			.defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
			.requestFactory(requestFactory)
			.build();
	}
}
