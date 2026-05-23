package team.po.feature.checklist.ai;

import java.util.List;
import java.util.Map;

public final class ChecklistAdviceSchema {

	public static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
		"type", "OBJECT",
		"properties", Map.of(
			"summary", Map.of(
				"type", "STRING",
				"description", "이 작업에서 가장 중요한 포인트를 요약한 한 문장"
			),
			"recommendedFlow", Map.of(
				"type", "ARRAY",
				"minItems", 3,
				"maxItems", 5,
				"items", Map.of("type", "STRING")
			),
			"considerations", Map.of(
				"type", "ARRAY",
				"minItems", 2,
				"maxItems", 4,
				"items", Map.of("type", "STRING")
			),
			"improvementPoints", Map.of(
				"type", "ARRAY",
				"minItems", 1,
				"maxItems", 3,
				"items", Map.of("type", "STRING")
			)
		),
		"required", List.of("summary", "recommendedFlow", "considerations", "improvementPoints"),
		"propertyOrdering", List.of("summary", "recommendedFlow", "considerations", "improvementPoints")
	);

	private ChecklistAdviceSchema() {
	}
}
