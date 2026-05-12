package team.po.feature.devguide.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public record DevGuideContent(
	String overview,
	List<TechStackItem> techStack,
	List<MvpPriority> mvpPriorities,
	List<DecisionPoint> decisionPoints,
	List<Milestone> milestones
) {
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TechStackItem(String category, String recommendation, String reason) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record MvpPriority(int priority, String feature, String rationale) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record DecisionPoint(String topic, List<String> options, String consideration) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Milestone(int week, String goal, String deliverable) {
	}
}
