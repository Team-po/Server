package team.po.feature.devguide.dto;

import java.util.List;
import java.util.Objects;
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
		validateOverview();
		validateTechStack();
		validateMvpPriorities();
		validateDecisionPoints();
		validateMilestones();
	}

	private void validateOverview() {
		if (isBlank(overview)) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}
	}

	private void validateTechStack() {
		if (techStack == null || techStack.size() < 5 || techStack.size() > 7) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}

		for (TechStackItem item : techStack) {
			if (item == null
				|| isBlank(item.category())
				|| isBlank(item.recommendation())
				|| isBlank(item.reason())) {
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}
		}
	}

	private void validateMvpPriorities() {
		if (mvpPriorities == null || mvpPriorities.size() != 3 || mvpPriorities.stream().anyMatch(Objects::isNull)) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}

		Set<Integer> priorities = mvpPriorities.stream()
			.map(MvpPriority::priority)
			.collect(Collectors.toSet());

		if (!priorities.equals(Set.of(1, 2, 3))) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}

		for (MvpPriority p : mvpPriorities) {
			if (isBlank(p.feature()) || isBlank(p.rationale())) {
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}

			if (p.subFeatures() == null || p.subFeatures().size() != 3) {
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}

			if (p.subFeatures().stream().anyMatch(this::isBlank)) {
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}
		}
	}

	private void validateDecisionPoints() {
		if (decisionPoints == null || decisionPoints.size() < 3 || decisionPoints.size() > 5) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}

		for (DecisionPoint d : decisionPoints) {
			if (d == null
				|| isBlank(d.topic())
				|| isBlank(d.consideration())) {
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}

			if (d.options() == null || d.options().size() < 2 || d.options().size() > 3) {
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}

			if (d.options().stream().anyMatch(this::isBlank)) {
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

		Set<Integer> expected = IntStream.rangeClosed(1, 12)
			.boxed()
			.collect(Collectors.toSet());

		if (!weeks.equals(expected)) {
			throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
		}

		for (Milestone m : milestones) {
			if (m == null || isBlank(m.goal())) {
				throw new ApplicationException(ErrorCode.GEMINI_INVALID_RESPONSE);
			}

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
