package team.po.feature.match.strategy;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.enums.Role;

public interface MatchingStrategy {
	// 신규 매칭
	Optional<MatchingResult> findTeamCandidates(MatchingContext context);

	// 빈자리 충원
	Optional<ProjectRequest> findCandidateForRole(Role role, List<ProjectRequest> pool, int hostLevel,
		Set<Long> blacklist);
}
