package team.po.common.ai.client;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;
import team.po.common.ai.dto.GeminiRequest;
import team.po.common.ai.dto.GeminiResponse;
import team.po.config.GeminiProperties;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;

@Slf4j
@Component
public class GeminiClient {

	private final RestClient geminiRestClient;
	private final GeminiProperties geminiProperties;

	public GeminiClient(
		@Qualifier("geminiRestClient") RestClient geminiRestClient,
		GeminiProperties geminiProperties
	) {
		this.geminiRestClient = geminiRestClient;
		this.geminiProperties = geminiProperties;
	}

	public String generateStructuredJson(String prompt, Map<String, Object> schema) {
		this.validateConfiguration();
		log.info("Gemini API 호출 시작: model={}, promptLength={}", geminiProperties.model(), prompt.length());

		try {
			GeminiResponse response = geminiRestClient.post()
				.uri("/models/{model}:generateContent", geminiProperties.model())
				.body(GeminiRequest.ofStructured(
					prompt,
					schema,
					geminiProperties.temperature(),
					geminiProperties.maxOutputTokens()
				))
				.retrieve()
				.body(GeminiResponse.class);

			if (response == null) {
				log.error("Gemini API 응답이 null");
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}

			String json = response.extractText();
			if (!StringUtils.hasText(json)) {
				log.error("Gemini API 응답 텍스트가 비어 있음");
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}

			log.info("Gemini API 호출 완료: jsonLength={}", json.length());
			return json;
		} catch (RestClientResponseException exception) {
			log.error("Gemini API 응답 오류: status={}", exception.getStatusCode());
			throw new ApplicationException(ErrorCode.GEMINI_API_ERROR);
		} catch (RestClientException exception) {
			log.error("Gemini API 호출 실패: cause={}", exception.getClass().getSimpleName());
			throw new ApplicationException(ErrorCode.GEMINI_API_ERROR);
		}
	}

	private void validateConfiguration() {
		if (!StringUtils.hasText(geminiProperties.apiKey())
			|| !StringUtils.hasText(geminiProperties.baseUrl())
			|| !StringUtils.hasText(geminiProperties.model())) {
			throw new ApplicationException(ErrorCode.GEMINI_API_ERROR, "Gemini 설정이 올바르지 않습니다.");
		}
	}
}
