package team.po.feature.match.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.feature.match.domain.MatchingMember;
import team.po.feature.match.domain.MatchingSession;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.enums.Role;
import team.po.feature.match.repository.MatchingMemberRepository;
import team.po.feature.match.repository.MatchingSessionRepository;
import team.po.feature.match.repository.ProjectRequestRepository;
import team.po.feature.match.strategy.MatchConstants;
import team.po.feature.match.strategy.MatchingContext;
import team.po.feature.match.strategy.MatchingStrategy;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchScheduler {
	private static final int HOST_BATCH_LIMIT = 50;

	private final ProjectRequestRepository projectRequestRepository;
	private final MatchingSessionRepository matchingSessionRepository;
	private final MatchingMemberRepository matchingMemberRepository;
	private final MatchService matchService;
	private final MatchingStrategy matchingStrategy;

	@Scheduled(fixedDelay = 60_000) // 1분마다 실행
	public void runMatchingCycle() {
		String cycleId = UUID.randomUUID().toString().substring(0, 8);
		MDC.put("cycleId", cycleId);

		try {
			log.info("매칭 사이클 시작");
			// 1. waiting pool 로드
			Map<Role, List<ProjectRequest>> waitingPool = loadWaitingMemberPool();
			// 2. 빈자리 충원 프로세스 (기존 세션 우선)
			processVacancies(waitingPool);
			// 3. 신규 매칭 프로세스
			processNewMatching(waitingPool);

			log.info("매칭 사이클 완료");
		} catch (Exception e) {
			log.error("매칭 사이클 실행 중 예외 발생", e);
		} finally {
			MDC.remove("cycleId");
		}
	}

	// 신규 매칭 세션 생성
	private void processNewMatching(Map<Role, List<ProjectRequest>> waitingPool) {
		List<ProjectRequest> hosts = projectRequestRepository.findWaitingHosts(PageRequest.of(0, HOST_BATCH_LIMIT));
		log.debug("New Host Candidate: {}명", hosts.size());

		for (ProjectRequest host : hosts) {
			try {
				// 블랙리스트 - host 본인 제외
				Set<Long> blacklist = new HashSet<>();
				blacklist.add(host.getUser().getId());

				MatchingContext context = new MatchingContext(host, waitingPool, blacklist);
				matchingStrategy.findTeamCandidates(context).ifPresent(result -> {
					List<ProjectRequest> candidates = result.selectedCandidates();
					matchService.createMatchingSession(host, candidates);

					// 매칭된 인원은 pool에서 제거 (사이클 내 중복 매칭 방지)
					Set<Long> matchedIds = candidates.stream()
						.map(c -> c.getUser().getId())
						.collect(Collectors.toSet());
					matchedIds.add(host.getUser().getId());
					removeMatchedFromPool(waitingPool, matchedIds);
				});
			} catch (Exception e) {
				log.error("신규 매칭 세션 생성 실패: hostId={}", host.getId(), e);
			}
		}
	}

	// 기존 세션 빈자리 충원
	private void processVacancies(Map<Role, List<ProjectRequest>> waitingPool) {
		List<MatchingSession> activeSessions = matchingSessionRepository.findAllActive();

		for (MatchingSession session : activeSessions) {
			try {
				fillSessionVacancy(session, waitingPool);
			} catch (Exception e) {
				log.error("빈자리 충원 실패: sessionId={}", session.getId(), e);
			}
		}
	}

	private void fillSessionVacancy(MatchingSession session, Map<Role, List<ProjectRequest>> waitingPool) {
		List<MatchingMember> currentMembers = matchingMemberRepository.findAllActiveBySessionIdWithFetch(
			session.getId());
		if (currentMembers.size() >= MatchConstants.TEAM_SIZE)
			return;

		List<ProjectRequest> allPrs = currentMembers.stream()
			.map(MatchingMember::getProjectRequest)
			.toList();

		// Host Project Request 조회
		ProjectRequest hostPr = allPrs.stream()
			.filter(ProjectRequest::isHostRequest)
			.findFirst()
			.orElse(null);

		if (hostPr == null) {
			log.error("호스트 매칭 요청 조회 실패 - 세션 정리 시작: sessionId={}, memberCount={}", session.getId(),
				currentMembers.size());
			matchService.abandonOrphanSession(session.getId());
			return;
		}

		// 현재 세션 구성 - 부족한 포지션 파악
		int hostLevel = hostPr.getUser().getLevel();
		Map<Role, Long> currentRoleCount = calculateCurrentRoleCounts(allPrs);
		Map<Role, Integer> targetComposition = MatchConstants.REQUIRED_NON_HOST.get(hostPr.getRole());

		// 블랙리스트 구성 (이미 거절한 유저 + 현재 참여 중인 유저)
		Set<Long> blacklist = new HashSet<>(
			matchingMemberRepository.findRejectedUserIdsBySessionId(session.getId())
		);
		currentMembers.forEach(m -> blacklist.add(m.getUser().getId()));

		// 부족한 포지션별로 scoring 적용
		for (Map.Entry<Role, Integer> entry : targetComposition.entrySet()) {
			Role role = entry.getKey();
			int needed = entry.getValue() - currentRoleCount.getOrDefault(role, 0L).intValue();
			List<ProjectRequest> rolePool = waitingPool.getOrDefault(role, List.of());

			for (int i = 0; i < needed; i++) {
				Optional<ProjectRequest> candidate = matchingStrategy
					.findCandidateForRole(role, rolePool, hostLevel, blacklist);

				if (candidate.isEmpty())
					break;

				ProjectRequest selected = candidate.get();
				matchService.fillVacancy(session.getId(), role, selected);

				// 트랜잭션 완료 후 pool 및 blacklist 업데이트
				blacklist.add(selected.getUser().getId());
				removeMatchedFromPool(waitingPool, Set.of(selected.getUser().getId()));
			}
		}

	}

	private Map<Role, List<ProjectRequest>> loadWaitingMemberPool() {
		Map<Role, List<ProjectRequest>> pool = new HashMap<>();
		for (Role role : Role.values()) {
			pool.put(role, new ArrayList<>(projectRequestRepository.findWaitingMembersByRole(role)));
		}
		return pool;
	}

	private Map<Role, Long> calculateCurrentRoleCounts(List<ProjectRequest> allPrs) {
		return allPrs.stream()
			.filter(pr -> !pr.isHostRequest())
			.collect(Collectors.groupingBy(ProjectRequest::getRole, Collectors.counting()));
	}

	private void removeMatchedFromPool(Map<Role, List<ProjectRequest>> pool, Set<Long> matchedUserIds) {
		pool.values().forEach(list -> list.removeIf(pr -> matchedUserIds.contains(pr.getUser().getId())));
	}
}
