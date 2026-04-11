package team.po.feature.projectgroup.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
	public CreateProjectGroupResponse createProjectGroup(CreateProjectGroupRequest request) {
		this.validateCreateRequest(request);

		Long groupId = request.groupId();
		List<CreateProjectGroupMemberRequest> requestMembers = request.members();

		log.info("팀 스페이스 생성 요청: groupId={}, memberCount={}", groupId, requestMembers.size());

		List<Long> userIds = requestMembers.stream()
			.map(CreateProjectGroupMemberRequest::userId)
			.toList();
		this.validateMembers(requestMembers, userIds);

		List<Users> users = userRepository.findAllByIdInAndDeletedAtIsNull(userIds);
		if (users.size() != userIds.size()) {
			log.warn("팀 스페이스 생성 실패: 존재하지 않는 사용자 포함, groupId={}, userIds={}", groupId, userIds);
			throw new ProjectGroupException(ProjectGroupErrorType.PROJECT_GROUP_MEMBER_NOT_FOUND);
		}
		if (projectGroupMemberRepository.existsByUser_IdIn(userIds)) {
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
				.groupId(groupId)
				.projectDescription(request.projectDescription())
				.projectMvp(request.projectMvp())
				.status(request.status() == null ? ProjectGroupStatus.ACTIVE : request.status())
				.build());

			List<ProjectGroupMember> members = new ArrayList<>();
			for (CreateProjectGroupMemberRequest memberRequest : requestMembers) {
				Users user = usersById.get(memberRequest.userId());
				ProjectGroupMember member = new ProjectGroupMember(
					projectGroup,
					user,
					memberRequest.role(),
					memberRequest.groupRole()
				);

				if (memberRequest.groupRole() == GroupRole.MEMBER && Boolean.TRUE.equals(memberRequest.admin())) {
					member.grantAdmin();
				}

				members.add(member);
			}
			projectGroupMemberRepository.saveAllAndFlush(members);

			log.info("팀 스페이스 생성 완료: projectGroupId={}, groupId={}, memberCount={}",
				projectGroup.getId(), groupId, members.size());

			return new CreateProjectGroupResponse(
				projectGroup.getId(),
				projectGroup.getProjectName(),
				projectGroup.getProjectTitle(),
				projectGroup.getStatus().name(),
				members.size()
			);
		} catch (DataIntegrityViolationException exception) {
			ProjectGroup duplicated = projectGroupRepository.findByGroupId(groupId).orElse(null);
			if (duplicated != null) {
				List<ProjectGroupMember> duplicatedMembers = projectGroupMemberRepository.findAllByProjectGroup_Id(
					duplicated.getId()
				);
				this.validateIdempotentPayload(duplicated, duplicatedMembers, request);
				log.info("동시 요청으로 이미 생성된 팀 스페이스 반환: groupId={}, projectGroupId={}",
					groupId, duplicated.getId());
				return new CreateProjectGroupResponse(
					duplicated.getId(),
					duplicated.getProjectName(),
					duplicated.getProjectTitle(),
					duplicated.getStatus().name(),
					duplicatedMembers.size()
				);
			}
			if (projectGroupMemberRepository.existsByUser_IdIn(userIds)) {
				throw new ProjectGroupException(
					ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
					"이미 팀에 속한 사용자가 포함되어 있습니다."
				);
			}
			log.warn("팀 스페이스 생성 중 데이터 무결성 오류 발생: groupId={}, userIds={}",
				groupId, userIds, exception);
			throw new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"팀 스페이스 저장 중 데이터 충돌이 발생했습니다."
			);
		}
	}

	@Transactional
	public void grantAdminPermission(Long projectGroupId, Long requesterUserId, Long targetUserId) {
		this.changeAdminPermission(projectGroupId, requesterUserId, targetUserId, true);
	}

	@Transactional
	public void revokeAdminPermission(Long projectGroupId, Long requesterUserId, Long targetUserId) {
		this.changeAdminPermission(projectGroupId, requesterUserId, targetUserId, false);
	}

	private void validateCreateRequest(CreateProjectGroupRequest request) {
		if (request == null
			|| request.groupId() == null
			|| request.members() == null) {
			throw new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"팀 스페이스 생성 요청이 올바르지 않습니다."
			);
		}

		if (request.projectName() == null || request.projectName().isBlank()) {
			throw new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"프로젝트 그룹 이름은 비어 있을 수 없습니다."
			);
		}
		if (request.projectTitle() == null || request.projectTitle().isBlank()) {
			throw new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"프로젝트 제목은 비어 있을 수 없습니다."
			);
		}
	}

	private void validateMembers(
		List<CreateProjectGroupMemberRequest> members,
		List<Long> userIds
	) {
		for (CreateProjectGroupMemberRequest member : members) {
			if (member == null
				|| member.userId() == null
				|| member.role() == null
				|| member.groupRole() == null) {
				throw new ProjectGroupException(
					ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
					"팀 구성원 정보가 올바르지 않습니다."
				);
			}
		}

		if (userIds.size() != 4) {
			throw new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"팀 인원은 정확히 4명이어야 합니다."
			);
		}

		if (new HashSet<>(userIds).size() != userIds.size()) {
			throw new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"팀 구성원 목록에 중복된 사용자 식별자가 포함되어 있습니다."
			);
		}

		long hostCount = members.stream()
			.filter(member -> member.groupRole() == GroupRole.HOST)
			.count();
		if (hostCount != 1) {
			throw new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"HOST는 정확히 1명이어야 합니다."
			);
		}
	}

	private void validateIdempotentPayload(
		ProjectGroup existing,
		List<ProjectGroupMember> existingMembers,
		CreateProjectGroupRequest request
	) {
		boolean sameProjectInfo = Objects.equals(existing.getProjectName(), request.projectName().trim())
			&& Objects.equals(existing.getProjectTitle(), request.projectTitle().trim())
			&& Objects.equals(existing.getProjectDescription(), request.projectDescription())
			&& Objects.equals(existing.getProjectMvp(), request.projectMvp())
			&& existing.getStatus() == (request.status() == null ? ProjectGroupStatus.ACTIVE : request.status());

		if (!sameProjectInfo) {
			throw new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"동일한 그룹 식별자로 서로 다른 프로젝트 정보를 생성할 수 없습니다."
			);
		}

		List<MemberSnapshot> requestMemberSnapshots = request.members().stream()
			.map(this::toMemberSnapshot)
			.sorted(Comparator.comparing(MemberSnapshot::userId))
			.toList();
		List<MemberSnapshot> existingMemberSnapshots = existingMembers.stream()
			.map(this::toMemberSnapshot)
			.sorted(Comparator.comparing(MemberSnapshot::userId))
			.toList();

		if (!requestMemberSnapshots.equals(existingMemberSnapshots)) {
			throw new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"동일한 그룹 식별자로 서로 다른 구성원 정보를 생성할 수 없습니다."
			);
		}
	}

	private void changeAdminPermission(
		Long projectGroupId,
		Long requesterUserId,
		Long targetUserId,
		boolean grant
	) {
		ProjectGroupMember hostMember = projectGroupMemberRepository
			.findByProjectGroup_IdAndGroupRole(projectGroupId, GroupRole.HOST)
			.orElseThrow(() -> new ProjectGroupException(
				ProjectGroupErrorType.INVALID_PROJECT_GROUP_REQUEST,
				"팀 스페이스의 방장 정보를 찾을 수 없습니다."
			));

		if (!hostMember.getUser().getId().equals(requesterUserId)) {
			throw new ProjectGroupException(
				ProjectGroupErrorType.PROJECT_GROUP_PERMISSION_DENIED,
				"방장만 관리자 권한을 변경할 수 있습니다."
			);
		}

		ProjectGroupMember targetMember = projectGroupMemberRepository
			.findByProjectGroup_IdAndUser_Id(projectGroupId, targetUserId)
			.orElseThrow(() -> new ProjectGroupException(
				ProjectGroupErrorType.PROJECT_GROUP_MEMBER_NOT_FOUND,
				"권한을 변경할 팀 멤버를 찾을 수 없습니다."
			));

		if (!grant && targetMember.getGroupRole() == GroupRole.HOST) {
			throw new ProjectGroupException(
				ProjectGroupErrorType.PROJECT_GROUP_PERMISSION_DENIED,
				"방장의 관리자 권한은 회수할 수 없습니다."
			);
		}

		if (grant) {
			targetMember.grantAdmin();
			return;
		}
		targetMember.revokeAdmin();
	}

	private MemberSnapshot toMemberSnapshot(CreateProjectGroupMemberRequest member) {
		boolean admin = member.groupRole() == GroupRole.HOST || Boolean.TRUE.equals(member.admin());
		return new MemberSnapshot(member.userId(), member.role(), member.groupRole(), admin);
	}

	private MemberSnapshot toMemberSnapshot(ProjectGroupMember member) {
		return new MemberSnapshot(
			member.getUser().getId(),
			member.getMemberRole(),
			member.getGroupRole(),
			member.isAdmin()
		);
	}

	private record MemberSnapshot(
		Long userId,
		team.po.feature.projectgroup.domain.MemberRole role,
		GroupRole groupRole,
		boolean admin
	) {
	}

}
