package team.po.feature.match.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.exception.ErrorCodeConstants;
import team.po.feature.match.domain.MatchingMember;
import team.po.feature.match.domain.MatchingSession;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.dto.MatchMemberResponse;
import team.po.feature.match.dto.MatchProjectResponse;
import team.po.feature.match.enums.Role;
import team.po.feature.match.event.MatchCreatedEvent;
import team.po.feature.match.exception.MatchAccessDeniedException;
import team.po.feature.match.exception.MatchDataIntegrityException;
import team.po.feature.match.repository.MatchingMemberRepository;
import team.po.feature.match.repository.MatchingSessionRepository;
import team.po.feature.match.repository.ProjectRequestRepository;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {
	private final MatchingSessionRepository matchingSessionRepository;
	private final MatchingMemberRepository matchingMemberRepository;
	private final ProjectRequestRepository projectRequestRepository;
	private final UserRepository userRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final ProjectGroupService projectGroupService;

	// 신규 매칭 세션 생성
	@Transactional
	public void createMatchingSession(ProjectRequest host, List<ProjectRequest> members) {
		// 1. 매칭 세션 생성 및 저장
		MatchingSession session = matchingSessionRepository.save(MatchingSession.create());

		// 2. Host 정보 등록 및 요청 상태 변경 (WAITING -> MATCHING)
		matchingMemberRepository.save(
			MatchingMember.createForHost(session.getId(), host.getId(), host.getUser().getId())
		);
		host.startMatching();

		// 3. 멤버 정보 등록 및 상태 변경 (WAITING -> MATCHING)
		for (ProjectRequest member : members) {
			matchingMemberRepository.save(
				MatchingMember.createForMember(session.getId(), member.getId(), member.getUser().getId())
			);
			member.startMatching();
		}

		// 4. userId 목록
		List<Long> allUserIds = new ArrayList<>();
		allUserIds.add(host.getUser().getId());
		members.forEach(m -> allUserIds.add(m.getUser().getId()));

		// 5. 매칭 생성 이벤트 발행 (AFTER_COMMIT 시점에)
		eventPublisher.publishEvent(new MatchCreatedEvent(session.getId(), allUserIds));

		log.info("매칭 세션 생성 완료: sessionId={}, hostId={}, memberCount={}",
			session.getId(), host.getId(), members.size());
	}

	// 기존 매칭 세션의 빈자리 채우기
	@Transactional
	public void fillVacancy(MatchingSession session, Role role, ProjectRequest candidate) {
		// 1. 새로운 멤버 등록 (MatchingMember 생성)
		matchingMemberRepository.save(
			MatchingMember.createForMember(session.getId(), candidate.getId(), candidate.getUser().getId())
		);

		// 2. 후보자 상태 변경 (WAITING -> MATCHING)
		candidate.startMatching();

		// 3. 이벤트 발행 (새로 합류한 멤버에게만)
		eventPublisher.publishEvent(new MatchCreatedEvent(session.getId(), List.of(candidate.getUser().getId())));

		log.info("매칭 세션 빈자리 증원 완료: sessionId={}, role={}, newMemberId={}",
			session.getId(), role, candidate.getId());
	}

	// 매칭 세션 멤버 목록 조회
	@Transactional(readOnly = true)
	public MatchMemberResponse getMatchMembers(Long matchId, Users loginUser) {
		// 1. 매칭 세션 접근 권한 확인 및 멤버 조회
		List<MatchingMember> members = validateMatchAccessAndGetMembers(matchId, loginUser.getId());

		// 2. 멤버 데이터 일괄 조회
		List<Long> userIds = members.stream().map(MatchingMember::getUserId).distinct().toList();
		List<Long> prIds = members.stream().map(MatchingMember::getProjectRequestId).distinct().toList();

		Map<Long, Users> userMap = userRepository.findAllById(userIds).stream()
			.filter(u -> u.getDeletedAt() == null)
			.collect(Collectors.toMap(Users::getId, user -> user));
		Map<Long, ProjectRequest> prMap = projectRequestRepository.findAllById(prIds).stream()
			.collect(Collectors.toMap(ProjectRequest::getId, pr -> pr));

		// 3. MatchingMember dto 매핑
		List<MatchMemberResponse.MemberDto> memberDtos = members.stream()
			.map(member -> {
				Users user = userMap.get(member.getUserId());
				ProjectRequest pr = prMap.get(member.getProjectRequestId());

				if (user == null || pr == null)
					return null;

				return new MatchMemberResponse.MemberDto(
					user.getId(),
					user.getNickname(),
					pr.getRole(),
					user.getLevel(),
					user.getTemperature(),
					user.getProfileImage(),
					pr.isHostRequest(),
					member.getIsAccepted()
				);
			})
			.filter(Objects::nonNull)
			.toList();

		return new MatchMemberResponse(matchId, memberDtos);
	}

	// 매칭 세션 프로젝트 정보 조회
	@Transactional(readOnly = true)
	public MatchProjectResponse getMatchProject(Long matchId, Users loginUser) {
		// 1. 매칭 세션 접근 권한 확인 및 멤버 조회
		List<MatchingMember> members = validateMatchAccessAndGetMembers(matchId, loginUser.getId());

		// 2. Host 검증 (단일 & 수락 상태)
		List<Long> prIds = members.stream().map(MatchingMember::getProjectRequestId).toList();
		Map<Long, ProjectRequest> prMap = projectRequestRepository.findAllById(prIds).stream()
			.collect(Collectors.toMap(ProjectRequest::getId, pr -> pr));

		List<MatchingMember> hosts = members.stream()
			.filter(m -> prMap.get(m.getProjectRequestId()).isHostRequest())
			.toList();

		if (hosts.size() != 1) {
			log.error("매칭 호스트 데이터 부정합: matchId={}, hostCount={}", matchId, hosts.size());
			throw new MatchDataIntegrityException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				ErrorCodeConstants.MATCH_DATA_ERROR,
				"해당 매칭 세션에 호스트 정보가 없거나 중복되었습니다."
			);
		}

		MatchingMember hostMember = hosts.getFirst();
		if (!Boolean.TRUE.equals(hostMember.getIsAccepted())) {
			log.error("호스트 수락 상태 부정합: matchId={}, userId={}", matchId, hostMember.getUserId());
			throw new MatchDataIntegrityException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				ErrorCodeConstants.MATCH_DATA_ERROR,
				"호스트의 매칭 수락 상태가 유효하지 않습니다."
			);
		}

		// 3. Host 프로젝트 정보 조회
		ProjectRequest hostPr = prMap.get(hostMember.getProjectRequestId());

		// 4. 매칭 세션 프로젝트 정보 반환
		return new MatchProjectResponse(
			matchId,
			hostPr.getProjectTitle(),
			hostPr.getProjectDescription(),
			hostPr.getProjectMvp()
		);
	}

	@Transactional
	public void accept(Long matchId, Users loginUser) {
		// 1. 매칭 세션 접근 권한 확인 및 멤버 조회
		List<MatchingMember> members = validateMatchAccessAndGetMembers(matchId, loginUser.getId());

		// 2. Host 여부 확인
		MatchingMember member = members.stream()
			.filter(m -> m.getUserId().equals(loginUser.getId()))
			.findFirst()
			.orElseThrow(() -> new MatchDataIntegrityException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				ErrorCodeConstants.MATCH_DATA_ERROR,
				"멤버 데이터 조회 실패"
			));

		// 3. 전체 매칭 요청 조회
		List<Long> prIds = members.stream().map(MatchingMember::getProjectRequestId).toList();
		List<ProjectRequest> allPrs = projectRequestRepository.findAllById(prIds);

		// 4. 호스트 여부 확인
		validateNotHost(member, allPrs);

		// 5. 멱등성 - 이미 수락한 경우 200 반환
		if (Boolean.TRUE.equals(member.getIsAccepted())) {
			log.info("이미 수락한 매칭 세션: matchId={}, userId={}", matchId, loginUser.getId());
			return;
		}

		// 6. 이미 거절한 경우 400
		if (Boolean.FALSE.equals(member.getIsAccepted())) {
			throw new MatchAccessDeniedException(
				HttpStatus.BAD_REQUEST,
				ErrorCodeConstants.MATCH_ACCESS_DENIED,
				"이미 거절한 매칭 세션입니다."
			);
		}

		// 7. 수락 처리
		member.accept();
		log.info("매칭 수락: matchId={}, userId={}", matchId, loginUser.getId());

		// 8. 전원 수락 여부 확인
		if (!matchingMemberRepository.isAllAccepted(matchId)) {
			eventPublisher.publishEvent(new MatchAcceptedEvent(matchId, loginUser.getId()));
			return;
		}

		// 9. 전원 수락 시 ProjectGroup 생성
		log.info("전원 수락 완료, ProjectGroup 생성 시작: matchId={}", matchId);
		completeMatching(matchId, members, allPrs);
	}

	private List<MatchingMember> validateMatchAccessAndGetMembers(Long matchId, Long userId) {
		// 1. 매칭 세션 존재 여부
		MatchingSession session = matchingSessionRepository.findByIdAndDeletedAtIsNull(matchId)
			.orElseThrow(() -> new MatchAccessDeniedException(
				HttpStatus.NOT_FOUND,
				ErrorCodeConstants.MATCH_NOT_FOUND,
				"존재하지 않는 매칭 세션입니다."
			));
		// 2. 해당 세션 매칭 멤버 전체 조회
		List<MatchingMember> members = matchingMemberRepository
			.findAllByMatchingSessionId(matchId);

		// 3. 세션 접근 권한 확인
		boolean isMember = members.stream()
			.anyMatch(m -> m.getUserId().equals(userId));
		// 멤버가 아니라면 접근 불가
		if (!isMember) {
			throw new MatchAccessDeniedException(
				HttpStatus.FORBIDDEN,
				ErrorCodeConstants.MATCH_ACCESS_DENIED,
				"해당 매칭 세션에 접근 권한이 없습니다."
			);
		}

		return members;
	}

	private void validateNotHost(MatchingMember member, List<ProjectRequest> allPrs) {
		ProjectRequest memberPr = allPrs.stream()
			.filter(p -> p.getId().equals(member.getProjectRequestId()))
			.findFirst()
			.orElseThrow(() -> new MatchDataIntegrityException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				ErrorCodeConstants.MATCH_DATA_ERROR,
				"매칭 요청 데이터 조회 실패"
			));
		if (memberPr.isHostRequest()) {
			throw new MatchAccessDeniedException(
				HttpStatus.FORBIDDEN,
				ErrorCodeConstants.MATCH_ACCESS_DENIED,
				"호스트는 수락 또는 거절할 수 없습니다."
			);
		}
	}

	private void completeMatching(Long matchId, List<MatchingMember> members, List<ProjectRequest> allPrs) {
		// 1. Host 매칭 요청 정보 조회
		ProjectRequest hostPr = allPrs.stream()
			.filter(ProjectRequest::isHostRequest)
			.findFirst()
			.orElseThrow(() -> new MatchDataIntegrityException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				ErrorCodeConstants.MATCH_DATA_ERROR,
				"호스트 데이터 조회 실패"
			));

		// 2. 매칭 요청 ID -> userID 매핑
		Map<Long, Long> prToUserId = members.stream()
			.collect(Collectors.toMap(
				MatchingMember::getProjectRequestId,
				MatchingMember::getUserId
			));

		// 3. CreateProjectGroupRequest 생성
		List<CreateProjectGroupMemberRequest> memberRequests = allPrs.stream()
			.map(pr -> new CreateProjectGroupMemberRequest(
				prToUserId.get(pr.getId()),
				MemberRole.valueOf(pr.getRole().name()),
				pr.isHostRequest() ? GroupRole.HOST : GroupRole.MEMBER
			))
			.toList();

		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			memberRequests,
			hostPr.getUser().getNickname() + "의 팀",
			hostPr.getProjectTitle(),
			hostPr.getProjectDescription(),
			hostPr.getProjectMvp()
		);

		// 4. ProjectGroup 생성
		CreateProjectGroupResponse response = projectGroupService.createProjectGroup(request);

		// 5. ProjectRequest 상태 변경
		allPrs.forEach(ProjectRequest::complete);

		// 6. 매칭 세션 비활성화
		matchingSessionRepository.findByIdAndDeletedAtIsNull(matchId)
			.ifPresent(MatchingSession::delete);

		// 7. 매칭 완료 이벤트 발행
		List<Long> memberUserIds = members.stream().map(MatchingMember::getUserId).toList();
		eventPublisher.publishEvent(
			new MatchCompletedEvent(matchId, response.projectGroupId(), memberUserIds)
		);

		log.info("매칭 완료 및 프로젝트 그룹 생성: matchId={}, projectGroupId={}",
			matchId, response.projectGroupId());
	}
}
