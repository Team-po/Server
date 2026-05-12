package team.po.feature.devguide.prompt;

import java.util.List;
import java.util.Map;

public final class DevGuideSchema {

	public static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
		"type", "OBJECT",
		"properties", Map.of(
			"overview", Map.of(
				"type", "STRING",
				"description", "프로젝트 개요 요약 (2~3문장)"
			),
			"techStack", Map.of(
				"type", "ARRAY",
				"minItems", 5,
				"maxItems", 7,
				"items", Map.of(
					"type", "OBJECT",
					"properties", Map.of(
						"category", Map.of("type", "STRING"),
						"recommendation", Map.of("type", "STRING"),
						"reason", Map.of("type", "STRING")
					),
					"required", List.of("category", "recommendation", "reason")
				)
			),
			"mvpPriorities", Map.of(
				"type", "ARRAY",
				"minItems", 3,
				"maxItems", 3,
				"items", Map.of(
					"type", "OBJECT",
					"properties", Map.of(
						"priority", Map.of("type", "INTEGER"),
						"feature", Map.of("type", "STRING"),
						"rationale", Map.of("type", "STRING"),
						"subFeatures", Map.of(
							"type", "ARRAY",
							"minItems", 3,
							"maxItems", 3,
							"items", Map.of("type", "STRING")
						)
					),
					"required", List.of("priority", "feature", "rationale", "subFeatures")
				)
			),
			"decisionPoints", Map.of(
				"type", "ARRAY",
				"minItems", 3,
				"maxItems", 5,
				"items", Map.of(
					"type", "OBJECT",
					"properties", Map.of(
						"topic", Map.of("type", "STRING"),
						"options", Map.of(
							"type", "ARRAY",
							"items", Map.of("type", "STRING")
						),
						"consideration", Map.of("type", "STRING")
					),
					"required", List.of("topic", "options", "consideration")
				)
			),
			"milestones", Map.of(
				"type", "ARRAY",
				"minItems", 12,
				"maxItems", 12,
				"items", Map.of(
					"type", "OBJECT",
					"properties", Map.of(
						"week", Map.of("type", "INTEGER"),
						"goal", Map.of("type", "STRING"),
						"roleTasks", Map.of(
							"type", "OBJECT",
							"properties", Map.of(
								"backend", Map.of("type", "STRING"),
								"frontend", Map.of("type", "STRING"),
								"design", Map.of("type", "STRING")
							),
							"required", List.of("backend", "frontend", "design")
						)
					),
					"required", List.of("week", "goal", "roleTasks")
				)
			)
		),
		"required", List.of("overview", "techStack", "mvpPriorities", "decisionPoints", "milestones"),
		"propertyOrdering", List.of("overview", "techStack", "mvpPriorities", "decisionPoints", "milestones")
	);

	private DevGuideSchema() {
	}
}