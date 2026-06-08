package team.po.feature.teamspace.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GithubWeeklySummaryContent(
	String summary,
	List<String> mainActivities,
	List<String> pullRequestHighlights,
	List<String> issueHighlights,
	List<String> followUpSuggestions
) {
	private static final int MAIN_ACTIVITY_MAX_COUNT = 5;
	private static final int PULL_REQUEST_HIGHLIGHT_MAX_COUNT = 5;
	private static final int ISSUE_HIGHLIGHT_MAX_COUNT = 5;
	private static final int FOLLOW_UP_SUGGESTION_MAX_COUNT = 3;

	public void validate() {
		if (isBlank(summary)) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}
		validateItems(mainActivities, MAIN_ACTIVITY_MAX_COUNT);
		validateItems(pullRequestHighlights, PULL_REQUEST_HIGHLIGHT_MAX_COUNT);
		validateItems(issueHighlights, ISSUE_HIGHLIGHT_MAX_COUNT);
		validateItems(followUpSuggestions, FOLLOW_UP_SUGGESTION_MAX_COUNT);
	}

	private void validateItems(List<String> values, int max) {
		if (values == null || values.size() > max) {
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
