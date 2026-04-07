package team.po.feature.projectgroup.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.MemberRole;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.dto.CreateProjectGroupMemberRequest;
import team.po.feature.projectgroup.dto.CreateProjectGroupRequest;
import team.po.feature.projectgroup.dto.CreateProjectGroupResponse;
import team.po.feature.projectgroup.exception.ProjectGroupErrorType;
import team.po.feature.projectgroup.exception.ProjectGroupException;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectGroupService {

	private final ProjectGroupRepository projectGroupRepository;
	private final ProjectGroupMemberRepository projectGroupMemberRepository;
	private final UserRepository userRepository;

	@Transactional
	public CreateProjectGroupResponse createProjectGroup(Long requesterUserId, CreateProjectGroupRequest request) {
		log.info("팀 스페이스 생성 요청: requesterUserId={}, hostUserId={}, matchId={}",
			requesterUserId, request.hostUserId(), request.matchId());
		this.validateRequesterPermission(requesterUserId, request.hostUserId());

		ProjectGroup existing = projectGroupRepository.findByMatchId(request.matchId()).orElse(null);
		if (existing != null) {
			return this.handleDuplicateMatchRequest(existing, request);
		}

		List<Long> userIds = request.members().stream()
			.map(CreateProjectGroupMemberRequest::userId)
			.toList();
		List<Long> uniqueUserIds = userIds.stream().distinct().toList();
		this.validateMembers(request.hostUserId(), userIds, uniqueUserIds);

		List<Users> users = userRepository.findAllByIdInAndDeletedAtIsNull(uniqueUserIds);
		if (users.size() != uniqueUserIds.size()) {
			log.warn("팀 스페이스 생성 실패: 존재하지 않는 사용자 포함, hostUserId={}, userIds={}", request.hostUserId(), uniqueUserIds);
			throw new ProjectGroupException(ProjectGroupErrorType.PROJECT_GROUP_MEMBER_NOT_FOUND);
		}
		if (projectGroupMemberRepository.existsByUser_IdIn(uniqueUserIds)) {
			throw new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"이미 팀에 속한 사용자가 포함되어 있습니다."
			);
		}

		Map<Long, Users> usersById = users.stream().collect(Collectors.toMap(Users::getId, Function.identity()));
		try {
			ProjectGroup projectGroup = projectGroupRepository.save(ProjectGroup.builder()
				.projectName(request.projectName().trim())
				.projectTitle(request.projectTitle().trim())
				.matchId(request.matchId())
				.projectDescription(request.projectDescription())
				.projectMvp(request.projectMvp())
				.status(request.status() == null ? ProjectGroupStatus.ACTIVE : request.status())
				.build());

			List<ProjectGroupMember> members = new ArrayList<>();
			for (CreateProjectGroupMemberRequest memberRequest : request.members()) {
				Users user = usersById.get(memberRequest.userId());
				boolean isHost = memberRequest.userId().equals(request.hostUserId());
				GroupRole groupRole = isHost ? GroupRole.HOST : GroupRole.MEMBER;
				MemberRole role = memberRequest.role();
				members.add(new ProjectGroupMember(projectGroup, user, role, groupRole));
			}
			projectGroupMemberRepository.saveAllAndFlush(members);

			log.info("팀 스페이스 생성 완료: groupId={}, hostUserId={}, memberCount={}",
				projectGroup.getId(), request.hostUserId(), members.size());

			return new CreateProjectGroupResponse(
				projectGroup.getId(),
				projectGroup.getProjectName(),
				projectGroup.getProjectTitle(),
				projectGroup.getStatus().name(),
				request.hostUserId(),
				members.size()
			);
		} catch (DataIntegrityViolationException exception) {
			ProjectGroup duplicated = projectGroupRepository.findByMatchId(request.matchId()).orElse(null);
			if (duplicated != null) {
				log.info("동시 요청으로 이미 생성된 팀 스페이스 반환: matchId={}, groupId={}",
					request.matchId(), duplicated.getId());
				return this.handleDuplicateMatchRequest(duplicated, request);
			}
			if (projectGroupMemberRepository.existsByUser_IdIn(uniqueUserIds)) {
				throw new ProjectGroupException(
					ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
					"이미 팀에 속한 사용자가 포함되어 있습니다."
				);
			}
			log.warn("팀 스페이스 생성 중 데이터 무결성 오류 발생: hostUserId={}, matchId={}, userIds={}",
				request.hostUserId(), request.matchId(), uniqueUserIds, exception);
			throw new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"팀 스페이스 저장 중 데이터 충돌이 발생했습니다."
			);
		}
	}

	private CreateProjectGroupResponse handleDuplicateMatchRequest(ProjectGroup existing, CreateProjectGroupRequest request) {
		List<ProjectGroupMember> existingMembers = projectGroupMemberRepository.findAllByProjectGroup_Id(existing.getId());
		Long existingHostUserId = existingMembers.stream()
			.filter(member -> member.getGroupRole() == GroupRole.HOST)
			.map(member -> member.getUser().getId())
			.findFirst()
			.orElseThrow(() -> new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"이미 생성된 팀 스페이스의 방장 정보를 찾을 수 없습니다."
			));

		if (!existingHostUserId.equals(request.hostUserId())) {
			throw new ProjectGroupException(ProjectGroupErrorType.PROJECT_GROUP_PERMISSION_DENIED);
		}

		this.validateIdempotentPayload(existing, request, existingMembers);

		log.info("중복 요청 감지: 기존 팀 스페이스 반환 groupId={}, matchId={}", existing.getId(), existing.getMatchId());
		return new CreateProjectGroupResponse(
			existing.getId(),
			existing.getProjectName(),
			existing.getProjectTitle(),
			existing.getStatus().name(),
			existingHostUserId,
			existingMembers.size()
		);
	}

	private void validateIdempotentPayload(
		ProjectGroup existing,
		CreateProjectGroupRequest request,
		List<ProjectGroupMember> existingMembers
	) {
		boolean sameProjectInfo = Objects.equals(existing.getProjectName(), request.projectName().trim())
			&& Objects.equals(existing.getProjectTitle(), request.projectTitle().trim())
			&& Objects.equals(existing.getProjectDescription(), request.projectDescription())
			&& Objects.equals(existing.getProjectMvp(), request.projectMvp())
			&& existing.getStatus() == (request.status() == null ? ProjectGroupStatus.ACTIVE : request.status());

		if (!sameProjectInfo) {
			throw new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"동일한 매칭 식별자로 서로 다른 프로젝트 정보를 생성할 수 없습니다."
			);
		}

		List<Long> requestMemberIds = request.members().stream()
			.map(CreateProjectGroupMemberRequest::userId)
			.sorted()
			.toList();
		List<Long> existingMemberIds = existingMembers.stream()
			.map(member -> member.getUser().getId())
			.sorted()
			.toList();

		if (!requestMemberIds.equals(existingMemberIds)) {
			throw new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"동일한 매칭 식별자로 서로 다른 구성원 목록을 생성할 수 없습니다."
			);
		}
	}

	private void validateRequesterPermission(Long requesterUserId, Long hostUserId) {
		if (!requesterUserId.equals(hostUserId)) {	//host가 팀스페이스 생성
			throw new ProjectGroupException(ProjectGroupErrorType.PROJECT_GROUP_PERMISSION_DENIED);
		}
	}

	private void validateMembers(Long hostUserId, List<Long> userIds, List<Long> uniqueUserIds) {
		if (userIds.size() != 4) {
			throw new ProjectGroupException(ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST, "팀 인원은 정확히 4명이어야 합니다.");
		}

		if (userIds.size() != uniqueUserIds.size()) {
			throw new ProjectGroupException(ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST, "팀 구성원 목록에 중복된 사용자 식별자가 포함되어 있습니다.");
		}

		if (!uniqueUserIds.contains(hostUserId)) {
			throw new ProjectGroupException(ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST, "방장 식별자는 팀 구성원 목록에 포함되어야 합니다.");
		}
	}
}
