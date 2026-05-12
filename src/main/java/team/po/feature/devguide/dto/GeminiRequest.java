package team.po.feature.devguide.dto;

import java.util.List;
import java.util.Map;

public record GeminiRequest(
	List<Content> contents,
	GenerationConfig generationConfig
) {
	public record Content(List<Part> parts) {
	}

	public record Part(String text) {
	}

	public record GenerationConfig(
		Double temperature,
		Integer maxOutputTokens,
		String responseMimeType,
		Map<String, Object> responseSchema
	) {
	}

	public static GeminiRequest ofStructured(String prompt, Map<String, Object> schema) {
		return new GeminiRequest(
			List.of(new Content(List.of(new Part(prompt)))),
			new GenerationConfig(0.7, 4096, "application/json", schema)
		);
	}
}
