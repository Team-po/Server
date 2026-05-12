package team.po.feature.devguide.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public record GeminiResponse(
	List<Candidate> candidates
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Candidate(Content content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Content(List<Part> parts) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Part(String text) {
	}

	public String extractText() {
		if (candidates == null || candidates.isEmpty()) {
			return "";
		}
		List<Part> parts = candidates.get(0).content().parts();
		if (parts == null || parts.isEmpty()) {
			return "";
		}
		return parts.get(0).text();
	}
}
