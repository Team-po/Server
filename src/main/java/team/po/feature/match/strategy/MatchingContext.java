package team.po.feature.match.strategy;

import java.util.List;
import java.util.Map;
import java.util.Set;

import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.enums.Role;

public record MatchingContext(
	ProjectRequest host, // 팀장
	Map<Role, List<ProjectRequest>> waitingPoolByRole, // 포지션별 대기 유저 풀
	Set<Long> blacklistUserIds
) {
}
