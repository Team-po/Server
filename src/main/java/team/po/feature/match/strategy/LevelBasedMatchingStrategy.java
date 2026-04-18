package team.po.feature.match.strategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.enums.Role;

@Slf4j
@Component
@RequiredArgsConstructor
public class LevelBasedMatchingStrategy implements MatchingStrategy {
	private final MatchingScorer matchingScorer;

	@Override
	public Optional<MatchingResult> findCandidates(MatchingContext context) {
		ProjectRequest host = context.host(); // host의 요청 정보
		Map<Role, List<ProjectRequest>> pool = context.waitingPoolByRole();
		Set<Long> blacklist = context.blacklistUserIds();

		int hostLevel = host.getUser().getLevel();

		// 1. host 포지션에 따른 팀 구성 정의
		Map<Role, Integer> required = MatchConstants.REQUIRED_NON_HOST.get(host.getRole());

		if (required == null) {
			log.error("정의되지 않은 호스트 포지션: userId={}, role={}", host.getId(), host.getRole());
			return Optional.empty(); // 해당 호스트 매칭 스킵
		}

		List<ProjectRequest> selected = new ArrayList<>();

		// 2. 포지션별 루프를 돌며 멤버 선발
		for (Map.Entry<Role, Integer> entry : required.entrySet()) {
			Role role = entry.getKey();
			int count = entry.getValue();

			List<ProjectRequest> candidates = pool.getOrDefault(role, List.of()).stream()
				.filter(pr -> !blacklist.contains(pr.getUser().getId())) // reject한 유저는 제외
				.filter(pr -> Math.abs(hostLevel - pr.getUser().getLevel()) <= MatchConstants.LEVEL_RANGE) // 레벨 필터
				.sorted(
					Comparator.comparingDouble((ProjectRequest pr) ->
							matchingScorer.caculateScore(host, pr))
						.reversed()
						.thenComparing(ProjectRequest::getCreatedAt)
				)
				.toList();

			// 매칭 후보가 부족하면 해당 host의 매칭은 이번 사이클에서 포기
			if (candidates.size() < count) {
				log.debug("매칭 후보 부족: hostId={}, role={}, required={}, found={}",
					host.getId(), role, count, candidates.size());
				return Optional.empty();
			}

			selected.addAll(candidates.subList(0, count));
		}

		return Optional.of(new MatchingResult(selected));
	}

}
