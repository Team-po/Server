package team.po.feature.checklist.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChecklistAiAdviceResponse(
	String summary,
	List<String> recommendedFlow,
	List<String> considerations,
	List<String> improvementPoints
) {
	public void validate() {
		if (isBlank(summary)) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}
		validateItems(recommendedFlow, 3, 5);
		validateItems(considerations, 2, 4);
		validateItems(improvementPoints, 1, 3);
	}

	private void validateItems(List<String> values, int min, int max) {
		if (values == null || values.size() < min || values.size() > max) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}
		if (values.stream().anyMatch(this::isBlank)) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
