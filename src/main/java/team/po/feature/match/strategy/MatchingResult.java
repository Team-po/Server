package team.po.feature.match.strategy;

import java.util.List;

import team.po.feature.match.domain.ProjectRequest;

public record MatchingResult(
	List<ProjectRequest> selectedCandidates // 최종 선택된 멤버 3명 (host 제외)
) {
}
