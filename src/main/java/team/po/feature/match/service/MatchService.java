package team.po.feature.match.service;

import java.util.ArrayList;
import java.util.List;

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
import team.po.feature.match.enums.Status;
import team.po.feature.match.event.MatchAcceptedEvent;
import team.po.feature.match.event.MatchCompletedEvent;
import team.po.feature.match.event.MatchCreatedEvent;
import team.po.feature.match.event.MatchMemberCanceledEvent;
import team.po.feature.match.event.MatchRejectedEvent;
import team.po.feature.match.event.MatchSessionDisbandedEvent;
import team.po.feature.match.exception.MatchAccessDeniedException;
import team.po.feature.match.exception.MatchDataIntegrityException;
import team.po.feature.match.exception.ProjectRequestNotFoundException;
import team.po.feature.match.repository.MatchingMemberRepository;
import team.po.feature.match.repository.MatchingSessionRepository;
import team.po.feature.match.repository.ProjectRequestRepository;
import team.po.feature.match.strategy.MatchConstants;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.MemberRole;
import team.po.feature.projectgroup.dto.CreateProjectGroupMemberRequest;
import team.po.feature.projectgroup.dto.CreateProjectGroupRequest;
import team.po.feature.projectgroup.dto.CreateProjectGroupResponse;
import team.po.feature.projectgroup.service.ProjectGroupService;
import team.po.feature.user.domain.Users;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {
	private final MatchingSessionRepository matchingSessionRepository;
	private final MatchingMemberRepository matchingMemberRepository;
	private final ProjectRequestRepository projectRequestRepository;
	private final ApplicationEventPublisher eventPublisher;
	private final ProjectGroupService projectGroupService;

	// 신규 매칭 세션 생성
	@Transactional
	public void createMatchingSession(ProjectRequest host, List<ProjectRequest> members) {
		// 1. 매칭 세션 생성 및 저장
		MatchingSession session = matchingSessionRepository.save(MatchingSession.create());

		// 2. Host 정보 등록 및 요청 상태 변경 (WAITING -> MATCHING)
		matchingMemberRepository.save(
			MatchingMember.createForHost(session, host)
		);
		host.startMatching();

		// 3. 멤버 정보 등록 및 상태 변경 (WAITING -> MATCHING)
		for (ProjectRequest member : members) {
			matchingMemberRepository.save(
				MatchingMember.createForMember(session, member)
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
			MatchingMember.createForMember(session, candidate)
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
		// 0. 이미 완료된 매칭 세션인지 검증
		MatchingSession session = matchingSessionRepository
			.findByIdAndDeletedAtIsNull(matchId)
			.orElseThrow(() -> new MatchAccessDeniedException(
				HttpStatus.NOT_FOUND,
				ErrorCodeConstants.MATCH_NOT_FOUND,
				"이미 완료되었거나 존재하지 않는 매칭 세션입니다."
			));

		// 1. 매칭 세션 접근 권한 확인 및 멤버 조회
		List<MatchingMember> members = validateMatchAccessAndGetMembers(session, loginUser.getId());

		// 2. MatchingMember dto
		List<MatchMemberResponse.MemberDto> memberDtos = members.stream()
			.map(mm -> new MatchMemberResponse.MemberDto(
				mm.getUser().getId(),
				mm.getUser().getNickname(),
				mm.getProjectRequest().getRole(),
				mm.getUser().getLevel(),
				mm.getUser().getTemperature(),
				mm.getUser().getProfileImage(),
				mm.getProjectRequest().isHostRequest(),
				mm.getIsAccepted()
			))
			.toList();

		return new MatchMemberResponse(matchId, memberDtos);
	}

	// 매칭 세션 프로젝트 정보 조회
	@Transactional(readOnly = true)
	public MatchProjectResponse getMatchProject(Long matchId, Users loginUser) {
		// 0. 이미 완료된 매칭 세션인지 검증
		MatchingSession session = matchingSessionRepository
			.findByIdAndDeletedAtIsNull(matchId)
			.orElseThrow(() -> new MatchAccessDeniedException(
				HttpStatus.NOT_FOUND,
				ErrorCodeConstants.MATCH_NOT_FOUND,
				"이미 완료되었거나 존재하지 않는 매칭 세션입니다."
			));

		// 1. 매칭 세션 접근 권한 확인 및 멤버 조회
		List<MatchingMember> members = validateMatchAccessAndGetMembers(session, loginUser.getId());

		// 2. Host 검증 (단일 & 수락 상태)
		List<MatchingMember> hosts = members.stream()
			.filter(mm -> mm.getProjectRequest().isHostRequest())
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
			log.error("호스트 수락 상태 부정합: matchId={}, userId={}", matchId, hostMember.getUser().getId());
			throw new MatchDataIntegrityException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				ErrorCodeConstants.MATCH_DATA_ERROR,
				"호스트의 매칭 수락 상태가 유효하지 않습니다."
			);
		}

		// 3. Host 프로젝트 정보 추출 및 응답
		ProjectRequest hostPr = hostMember.getProjectRequest();
		return new MatchProjectResponse(
			matchId,
			hostPr.getProjectTitle(),
			hostPr.getProjectDescription(),
			hostPr.getProjectMvp()
		);
	}

	@Transactional
	public void accept(Long matchId, Users loginUser) {
		// 0. 이미 완료된 매칭 세션인지 검증
		MatchingSession session = matchingSessionRepository
			.findByIdWithLock(matchId)
			.orElseThrow(() -> new MatchAccessDeniedException(
				HttpStatus.NOT_FOUND,
				ErrorCodeConstants.MATCH_NOT_FOUND,
				"이미 완료되었거나 존재하지 않는 매칭 세션입니다."
			));

		// 1. 매칭 세션 접근 권한 확인 및 멤버 조회
		List<MatchingMember> members = validateMatchAccessAndGetMembers(session, loginUser.getId());
		MatchingMember me = members.stream()
			.filter(m -> m.getUser().getId().equals(loginUser.getId())) // 리스트 중 내 ID와 일치하는 객체 찾기
			.findFirst()
			.orElseThrow(() -> new MatchDataIntegrityException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				ErrorCodeConstants.MATCH_DATA_ERROR,
				"매칭 세션 내에서 내 멤버 정보를 찾을 수 없습니다."
			));

		// 2. 호스트 여부 확인 - 호스트는 수락할 수 없음
		validateNotHost(me);

		// 3. 멱등성 - 이미 수락한 경우 200 반환
		if (Boolean.TRUE.equals(me.getIsAccepted())) {
			return;
		}

		// 4. 수락 처리
		me.accept();
		log.info("매칭 수락: matchId={}, userId={}", matchId, loginUser.getId());

		// 5. 전원 수락 여부 확인
		if (!matchingMemberRepository.isAllAccepted(matchId, MatchConstants.TEAM_SIZE)) {
			eventPublisher.publishEvent(new MatchAcceptedEvent(matchId, loginUser.getId()));
			return;
		}

		// 6. 전원 수락 시 ProjectGroup 생성
		log.info("전원 수락 완료, ProjectGroup 생성 시작: matchId={}", matchId);
		completeMatching(session, members);
	}

	@Transactional
	public void reject(Long matchId, Users loginUser) {
		// 0. 이미 완료된 매칭 세션인지 검증
		MatchingSession session = matchingSessionRepository
			.findByIdWithLock(matchId)
			.orElseThrow(() -> new MatchAccessDeniedException(
				HttpStatus.NOT_FOUND,
				ErrorCodeConstants.MATCH_NOT_FOUND,
				"이미 완료되었거나 존재하지 않는 매칭 세션입니다."
			));
		// 1. 매칭 세션 접근 권한 확인 및 멤버 조회
		List<MatchingMember> members = validateMatchAccessAndGetMembers(session, loginUser.getId());
		MatchingMember me = members.stream()
			.filter(m -> m.getUser().getId().equals(loginUser.getId())) // 리스트 중 내 ID와 일치하는 객체 찾기
			.findFirst()
			.orElseThrow(() -> new MatchDataIntegrityException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				ErrorCodeConstants.MATCH_DATA_ERROR,
				"매칭 세션 내에서 내 멤버 정보를 찾을 수 없습니다."
			));

		// 2. 호스트 여부 조회: 호스트는 거절 불가
		validateNotHost(me);

		// 3. 이미 거절한 경우 - soft delete

		// 4. 이미 수락한 경우 400
		if (Boolean.TRUE.equals(me.getIsAccepted())) {
			throw new MatchAccessDeniedException(
				HttpStatus.BAD_REQUEST,
				ErrorCodeConstants.MATCH_ACCESS_DENIED,
				"이미 수락한 매칭 세션입니다."
			);
		}

		// 5. 거절 처리
		me.reject();

		// 6. 해당 유저의 매칭 요청 상태 WAITING으로 초기화
		me.getProjectRequest().resetToWaiting();
		log.info("매칭 거절: matchId={}, userId={}", matchId, me.getUser().getId());

		// 7. 이벤트 발행
		List<Long> remainingUserIds = members.stream()
			.filter(m -> !m.getUser().getId().equals(loginUser.getId()))
			.map(m -> m.getUser().getId())
			.toList();

		eventPublisher.publishEvent(new MatchRejectedEvent(matchId, loginUser.getId(), remainingUserIds));
	}

	@Transactional
	public void cancel(Users loginUser) {
		// 1. 활성 매칭 요청 조회 (WAITING or MATCHING)
		ProjectRequest myPr = projectRequestRepository
			.findByUserIdAndStatusIn(loginUser.getId(), List.of(Status.WAITING, Status.MATCHING))
			.orElseThrow(() -> new ProjectRequestNotFoundException(
				HttpStatus.NOT_FOUND,
				ErrorCodeConstants.PROJECT_REQUEST_NOT_FOUND,
				"취소할 수 있는 매칭 요청이 없습니다."
			));

		// 2. WAITING: 단순 취소
		if (myPr.getStatus() == Status.WAITING) {
			myPr.cancel();
			log.info("매칭 요청 취소 - WAITING: prID={}, userId={}", myPr.getId(), loginUser.getId());
			return;
		}

		// 3. MATCHING - 세션 조회
		MatchingMember me = matchingMemberRepository
			.findCurrentActiveByUserId(loginUser.getId())
			.orElseThrow(() -> new MatchDataIntegrityException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				ErrorCodeConstants.MATCH_DATA_ERROR,
				"매칭 멤버 데이터 조회 실패"
			));
		List<MatchingMember> sessionMembers = matchingMemberRepository
			.findAllActiveBySessionIdWithFetch(me.getMatchingSession().getId());

		// 4. host 여부 확인 후 매칭 취소
		if (myPr.isHostRequest()) {
			cancelAsHost(me, sessionMembers);
		} else {
			cancelAsMember(me, sessionMembers);
		}
	}

	private void cancelAsMember(MatchingMember me, List<MatchingMember> sessionMembers) {
		Long sessionId = me.getMatchingSession().getId();
		List<Long> remainingUserIds = sessionMembers.stream()
			.filter(m -> !m.getUser().getId().equals(me.getUser().getId()))
			.map(m -> m.getUser().getId())
			.toList();

		// ProjectRequest status 변경
		me.getProjectRequest().cancel();
		// MatchingMember soft delete
		me.cancel();

		eventPublisher.publishEvent(
			new MatchMemberCanceledEvent(sessionId, me.getUser().getId(), remainingUserIds)
		);

		log.info("멤버 매칭 취소 완료: prId={}, userId={}, sessionId={}",
			me.getId(), me.getUser().getId(), sessionId);
	}

	private void cancelAsHost(MatchingMember me, List<MatchingMember> sessionMembers) {
		Long sessionId = me.getMatchingSession().getId();

		// 나머지 멤버 WAITING 복귀 + soft delete
		List<Long> restoredUserIds = new ArrayList<>();

		for (MatchingMember m : sessionMembers) {
			// host의 매칭 요청 취소
			if (m.getUser().getId().equals(me.getUser().getId())) {
				m.getProjectRequest().cancel();
			} else { // member의 매칭 요청 WAITING으로 초기화
				m.getProjectRequest().resetToWaiting();
				restoredUserIds.add(m.getUser().getId());
			}
			// MatchingMember 삭제
			m.cancel();
		}

		// 세션 비활성화
		me.getMatchingSession().delete();

		// 이벤트 발행
		eventPublisher.publishEvent(
			new MatchSessionDisbandedEvent(sessionId, me.getUser().getId(), restoredUserIds)
		);

		log.info("호스트 매칭 취소 및 세션 해산: sessionId={}, hostUserId={}, restoredCount={}",
			sessionId, me.getUser().getId(), restoredUserIds.size());
	}

	private List<MatchingMember> validateMatchAccessAndGetMembers(MatchingSession session, Long userId) {
		// 1. 해당 세션 매칭 멤버 전체 조회
		List<MatchingMember> members = matchingMemberRepository
			.findAllActiveBySessionIdWithFetch(session.getId());

		// 3. 세션 접근 권한 확인
		boolean isMember = members.stream()
			.anyMatch(m -> m.getUser().getId().equals(userId));
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

	private void validateNotHost(MatchingMember member) {
		if (member.getProjectRequest().isHostRequest()) {
			throw new MatchAccessDeniedException(
				HttpStatus.FORBIDDEN,
				ErrorCodeConstants.MATCH_ACCESS_DENIED,
				"호스트는 수락 또는 거절할 수 없습니다."
			);
		}
	}

	private void completeMatching(MatchingSession session, List<MatchingMember> members) {
		// 1. Host 매칭 요청 정보 조회
		MatchingMember hostMember = members.stream()
			.filter(m -> m.getProjectRequest().isHostRequest())
			.findFirst()
			.orElseThrow(() -> new MatchDataIntegrityException(
				HttpStatus.INTERNAL_SERVER_ERROR,
				ErrorCodeConstants.MATCH_DATA_ERROR,
				"호스트 데이터를 조회할 수 없습니다."
			));

		// 2. 프로젝트 그룹 생성 요청 리스트
		List<CreateProjectGroupMemberRequest> memberRequests = members.stream()
			.map(mm -> new CreateProjectGroupMemberRequest(
				mm.getUser().getId(),
				MemberRole.valueOf(mm.getProjectRequest().getRole().name()),
				mm.getProjectRequest().isHostRequest() ? GroupRole.HOST : GroupRole.MEMBER
			)).toList();

		// 3. Request 생성
		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			memberRequests,
			hostMember.getUser().getNickname() + "의 팀",
			hostMember.getProjectRequest().getProjectTitle(),
			hostMember.getProjectRequest().getProjectDescription(),
			hostMember.getProjectRequest().getProjectMvp()
		);

		// 4. ProjectGroup 생성
		CreateProjectGroupResponse response = projectGroupService.createProjectGroup(request);

		// 5. ProjectRequest 상태 변경
		members.forEach(mm -> mm.getProjectRequest().complete());

		// 6. 매칭 세션 비활성화
		session.delete();

		// 7. 매칭 완료 이벤트 발행
		List<Long> memberUserIds = members.stream().map(m -> m.getUser().getId()).toList();
		eventPublisher.publishEvent(
			new MatchCompletedEvent(session.getId(), response.projectGroupId(), memberUserIds)
		);

		log.info("매칭 완료 및 프로젝트 그룹 생성: matchId={}, projectGroupId={}",
			session.getId(), response.projectGroupId());
	}
}
