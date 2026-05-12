package team.po.feature.devguide.client;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.devguide.config.GeminiProperties;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.devguide.dto.GeminiRequest;
import team.po.feature.devguide.dto.GeminiResponse;

@Slf4j
@Component
public class GeminiClient {
	private final RestClient restClient;
	private final GeminiProperties properties;
	private final ObjectMapper objectMapper;

	public GeminiClient(GeminiProperties properties) {
		this.properties = properties;
		this.objectMapper = new ObjectMapper();
		this.restClient = RestClient.builder()
			.baseUrl(properties.baseUrl())
			.build();
	}

	/**
	 * Gemini API를 호출하여 개발 가이드라인을 생성한다.
	 *
	 * @param prompt  Gemini에게 전달할 프롬프트
	 * @param schema  Structured Output을 위한 JSON 스키마
	 * @return 파싱된 개발 가이드라인 콘텐츠
	 */
	public DevGuideContent generateDevGuide(String prompt, Map<String, Object> schema) {
		log.info("Gemini API 호출 시작: model={}, promptLength={}", properties.model(), prompt.length());

		try {
			GeminiResponse response = restClient.post()
				.uri("/models/{model}:generateContent?key={key}", properties.model(), properties.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.body(
					GeminiRequest.ofStructured(prompt, schema, properties.temperature(), properties.maxOutputTokens()))
				.retrieve()
				.body(GeminiResponse.class);

			if (response == null) {
				log.error("Gemini API 응답이 null");
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}

			String json = response.extractText();
			if (json.isBlank()) {
				log.error("Gemini API 응답에 텍스트 없음");
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}

			try {
				DevGuideContent content = objectMapper.readValue(json, DevGuideContent.class);
				log.info("Gemini API 호출 완료: jsonLength={}", json.length());
				return content;
			} catch (JsonProcessingException e) {
				log.error("Gemini 응답 JSON 파싱 실패: rawJsonLength={}", json.length(), e);
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}
		} catch (ApplicationException e) {
			throw e;
		} catch (RestClientException e) {
			log.error("Gemini API 호출 실패 (Network/HTTP)", e);
			throw new ApplicationException(ErrorCode.GEMINI_API_ERROR);
		} catch (Exception e) {
			log.error("Gemini API 호출 실패 (예상치 못한 오류)", e);
			throw new ApplicationException(ErrorCode.GEMINI_API_ERROR);
		}
	}
}
