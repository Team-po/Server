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

		Candidate candidate = candidates.get(0);
		if (candidate == null || candidate.content() == null) {
			return "";
		}

		List<Part> parts = candidate.content().parts();
		if (parts == null || parts.isEmpty()) {
			return "";
		}

		Part firstPart = parts.get(0);
		if (firstPart == null || firstPart.text == null) {
			return "";
		}

		return firstPart.text();
	}
}
