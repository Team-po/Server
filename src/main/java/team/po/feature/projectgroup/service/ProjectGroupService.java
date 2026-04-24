package team.po.feature.projectgroup.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.dto.CreateProjectGroupMemberRequest;
import team.po.feature.projectgroup.dto.CreateProjectGroupRequest;
import team.po.feature.projectgroup.dto.CreateProjectGroupResponse;
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
		return this.createFromMatch(request);
	}

	@Transactional
	public CreateProjectGroupResponse createFromMatch(CreateProjectGroupRequest request) {
		this.validateCreateRequest(request);

		List<CreateProjectGroupMemberRequest> requestMembers = request.members();
		log.info("매칭 결과 기반 팀 스페이스 생성 요청: memberCount={}", requestMembers.size());

		List<Long> userIds = requestMembers.stream()
			.map(CreateProjectGroupMemberRequest::userId)
			.toList();
		this.validateMembers(requestMembers, userIds);

		List<Users> users = userRepository.findAllByIdInAndDeletedAtIsNullForUpdate(userIds);
		if (users.size() != userIds.size()) {
			log.warn("팀 스페이스 생성 실패: 존재하지 않는 사용자 포함, userIds={}", userIds);
			throw new ApplicationException(ErrorCode.PROJECT_GROUP_MEMBER_NOT_FOUND);
		}

		if (projectGroupMemberRepository.existsByUser_IdInAndProjectGroup_Status(userIds, ProjectGroupStatus.ACTIVE)) {
			throw new ApplicationException(
				ErrorCode.INVALID_PROJECT_GROUP_REQUEST,
				"이미 ACTIVE 팀에 속한 사용자가 포함되어 있습니다."
			);
		}

		Map<Long, Users> usersById = users.stream().collect(Collectors.toMap(Users::getId, Function.identity()));

		ProjectGroup projectGroup = projectGroupRepository.save(ProjectGroup.builder()
			.projectName(request.projectName().trim())
			.projectTitle(request.projectTitle().trim())
			.projectDescription(request.projectDescription())
			.projectMvp(request.projectMvp())
			.status(ProjectGroupStatus.ACTIVE)
			.build());

		List<ProjectGroupMember> members = new ArrayList<>();
		for (CreateProjectGroupMemberRequest memberRequest : requestMembers) {
			Users user = usersById.get(memberRequest.userId());
			ProjectGroupMember member = ProjectGroupMember.builder()
				.projectGroup(projectGroup)
				.user(user)
				.memberRole(memberRequest.role())
				.groupRole(memberRequest.groupRole())
				.build();

			members.add(member);
		}
		projectGroupMemberRepository.saveAllAndFlush(members);

		log.info("팀 스페이스 생성 완료: projectGroupId={}, memberCount={}", projectGroup.getId(), members.size());

		return new CreateProjectGroupResponse(
			projectGroup.getId(),
			projectGroup.getProjectName(),
			projectGroup.getProjectTitle(),
			projectGroup.getStatus().name(),
			members.size()
		);
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
		if (request == null || request.members() == null) {
			throw new ApplicationException(
				ErrorCode.INVALID_PROJECT_GROUP_REQUEST,
				"팀 스페이스 생성 요청이 올바르지 않습니다."
			);
		}

		if (request.projectName() == null || request.projectName().isBlank()) {
			throw new ApplicationException(
				ErrorCode.INVALID_PROJECT_GROUP_REQUEST,
				"프로젝트 그룹 이름은 비어 있을 수 없습니다."
			);
		}
		if (request.projectTitle() == null || request.projectTitle().isBlank()) {
			throw new ApplicationException(
				ErrorCode.INVALID_PROJECT_GROUP_REQUEST,
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
				throw new ApplicationException(
					ErrorCode.INVALID_PROJECT_GROUP_REQUEST,
					"팀 구성원 정보가 올바르지 않습니다."
				);
			}
		}

		if (userIds.size() != 4) {
			throw new ApplicationException(
				ErrorCode.INVALID_PROJECT_GROUP_REQUEST,
				"팀 인원은 정확히 4명이어야 합니다."
			);
		}

		if (new HashSet<>(userIds).size() != userIds.size()) {
			throw new ApplicationException(
				ErrorCode.INVALID_PROJECT_GROUP_REQUEST,
				"팀 구성원 목록에 중복된 사용자 식별자가 포함되어 있습니다."
			);
		}

		long hostCount = members.stream()
			.filter(member -> member.groupRole() == GroupRole.HOST)
			.count();
		if (hostCount != 1) {
			throw new ApplicationException(
				ErrorCode.INVALID_PROJECT_GROUP_REQUEST,
				"HOST는 정확히 1명이어야 합니다."
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
			.orElseThrow(() -> new ApplicationException(
				ErrorCode.INVALID_PROJECT_GROUP_REQUEST,
				"팀 스페이스의 방장 정보를 찾을 수 없습니다."
			));

		if (hostMember.getProjectGroup().getStatus() == ProjectGroupStatus.FINISHED) {
			throw new ApplicationException(
				ErrorCode.PROJECT_GROUP_PERMISSION_DENIED,
				"종료된 팀 스페이스에서는 관리자 권한을 변경할 수 없습니다."
			);
		}

		if (!hostMember.getUser().getId().equals(requesterUserId)) {
			throw new ApplicationException(
				ErrorCode.PROJECT_GROUP_PERMISSION_DENIED,
				"방장만 관리자 권한을 변경할 수 있습니다."
			);
		}

		ProjectGroupMember targetMember = projectGroupMemberRepository
			.findByProjectGroup_IdAndUser_Id(projectGroupId, targetUserId)
			.orElseThrow(() -> new ApplicationException(
				ErrorCode.PROJECT_GROUP_MEMBER_NOT_FOUND,
				"권한을 변경할 팀 멤버를 찾을 수 없습니다."
			));

		if (!grant && targetMember.getGroupRole() == GroupRole.HOST) {
			throw new ApplicationException(
				ErrorCode.PROJECT_GROUP_PERMISSION_DENIED,
				"방장의 관리자 권한은 회수할 수 없습니다."
			);
		}

		if (grant) {
			targetMember.grantAdmin();
			return;
		}
		targetMember.revokeAdmin();
	}

}
