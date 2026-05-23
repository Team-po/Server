package team.po.feature.devguide.dto;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;

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
	public record MvpPriority(int priority, String feature, String rationale, List<String> subFeatures) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record DecisionPoint(String topic, List<String> options, String consideration) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Milestone(int week, String goal, RoleTasks roleTasks) {
	}

	public record RoleTasks(
		String backend,
		String frontend,
		String design
	) {
	}

	public void validate() {
		validateMvpPriorities();
		validateMilestones();
	}

	private void validateMvpPriorities() {
		if (mvpPriorities == null || mvpPriorities.size() != 3) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}
		Set<Integer> priorities = mvpPriorities.stream()
			.map(MvpPriority::priority)
			.collect(Collectors.toSet());
		if (!priorities.equals(Set.of(1, 2, 3))) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}

		// subFeatures 검증
		for (MvpPriority p : mvpPriorities) {
			if (p.subFeatures() == null || p.subFeatures().size() != 3) {
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}
			if (p.subFeatures().stream().anyMatch(this::isBlank)) {
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}
		}
	}

	private void validateMilestones() {
		if (milestones == null || milestones.size() != 12) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}
		Set<Integer> weeks = milestones.stream()
			.map(Milestone::week)
			.collect(Collectors.toSet());
		Set<Integer> expected = IntStream.rangeClosed(1, 12).boxed().collect(Collectors.toSet());
		if (!weeks.equals(expected)) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}
		// roleTasks 빈 문자열 검증
		for (Milestone m : milestones) {
			RoleTasks t = m.roleTasks();
			if (t == null
				|| isBlank(t.backend())
				|| isBlank(t.frontend())
				|| isBlank(t.design())) {
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}
		}
	}

	private boolean isBlank(String s) {
		return s == null || s.isBlank();
	}
}
