package team.po.feature.teamspace.ai;

import java.util.List;
import java.util.Map;

public final class GithubWeeklySummarySchema {

	public static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
		"type", "OBJECT",
		"properties", Map.of(
			"summary", Map.of(
				"type", "STRING",
				"description", "최근 7일 GitHub 활동을 사용자 관점에서 요약한 1~2문장"
			),
			"mainActivities", Map.of(
				"type", "ARRAY",
				"minItems", 0,
				"maxItems", 5,
				"items", Map.of("type", "STRING")
			),
			"pullRequestHighlights", Map.of(
				"type", "ARRAY",
				"minItems", 0,
				"maxItems", 5,
				"items", Map.of("type", "STRING")
			),
			"issueHighlights", Map.of(
				"type", "ARRAY",
				"minItems", 0,
				"maxItems", 5,
				"items", Map.of("type", "STRING")
			),
			"followUpSuggestions", Map.of(
				"type", "ARRAY",
				"minItems", 0,
				"maxItems", 3,
				"items", Map.of("type", "STRING")
			)
		),
		"required", List.of(
			"summary",
			"mainActivities",
			"pullRequestHighlights",
			"issueHighlights",
			"followUpSuggestions"
		),
		"propertyOrdering", List.of(
			"summary",
			"mainActivities",
			"pullRequestHighlights",
			"issueHighlights",
			"followUpSuggestions"
		)
	);

	private GithubWeeklySummarySchema() {
	}
}
