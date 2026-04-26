package team.po.feature.projectgroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.MemberRole;
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

@ExtendWith(MockitoExtension.class)
class ProjectGroupServiceTest {

	@Mock
	private ProjectGroupRepository projectGroupRepository;

	@Mock
	private ProjectGroupMemberRepository projectGroupMemberRepository;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private ProjectGroupService projectGroupService;

	@Test
	void createProjectGroup_savesGroupAndMembers_whenRequestIsValid() {
		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNullForUpdate(anyList())).thenReturn(matchedUsers);
		when(projectGroupMemberRepository.existsByUser_IdInAndProjectGroup_Status(anyList(), eq(ProjectGroupStatus.ACTIVE)))
			.thenReturn(false);
		when(projectGroupRepository.save(any(ProjectGroup.class))).thenAnswer(invocation -> {
			ProjectGroup saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 10L);
			return saved;
		});

		CreateProjectGroupResponse response = projectGroupService.createProjectGroup(defaultRequest());

		assertThat(response.projectGroupId()).isEqualTo(10L);
		assertThat(response.projectName()).isEqualTo("Teampo Alpha");
		assertThat(response.status()).isEqualTo("ACTIVE");
		assertThat(response.memberCount()).isEqualTo(4);

		ArgumentCaptor<List<ProjectGroupMember>> membersCaptor = ArgumentCaptor.forClass(List.class);
		verify(projectGroupMemberRepository).saveAllAndFlush(membersCaptor.capture());
		List<ProjectGroupMember> members = membersCaptor.getValue();
		assertThat(members).hasSize(4);
		assertThat(members.stream().filter(member -> member.getGroupRole() == GroupRole.HOST).count()).isEqualTo(1L);
		assertThat(members.stream().filter(ProjectGroupMember::isAdmin).count()).isEqualTo(1L);
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenMembersContainDuplicateUser() {
		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND, GroupRole.HOST),
				new CreateProjectGroupMemberRequest(1L, MemberRole.FRONTEND, GroupRole.MEMBER),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN, GroupRole.MEMBER),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND, GroupRole.MEMBER)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP"
		);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(request))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.INVALID_PROJECT_GROUP_REQUEST.getCode());
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenMemberSizeIsNotFour() {
		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND, GroupRole.HOST),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND, GroupRole.MEMBER),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN, GroupRole.MEMBER)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP"
		);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(request))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.INVALID_PROJECT_GROUP_REQUEST.getCode());
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenHostCountIsNotOne() {
		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND, GroupRole.MEMBER),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND, GroupRole.MEMBER),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN, GroupRole.MEMBER),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND, GroupRole.MEMBER)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP"
		);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(request))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.INVALID_PROJECT_GROUP_REQUEST.getCode());
	}

	@Test
	void createProjectGroup_savesGroup_whenHostExists() {
		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNullForUpdate(anyList())).thenReturn(matchedUsers);
		when(projectGroupMemberRepository.existsByUser_IdInAndProjectGroup_Status(anyList(), eq(ProjectGroupStatus.ACTIVE)))
			.thenReturn(false);
		when(projectGroupRepository.save(any(ProjectGroup.class))).thenAnswer(invocation -> {
			ProjectGroup saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 11L);
			return saved;
		});

		CreateProjectGroupResponse response = projectGroupService.createProjectGroup(defaultRequest());
		assertThat(response.projectGroupId()).isEqualTo(11L);
		assertThat(response.memberCount()).isEqualTo(4);
	}

	@Test
	void createProjectGroup_throwsNotFound_whenAnyMemberUserDoesNotExist() {
		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L));
		when(userRepository.findAllByIdInAndDeletedAtIsNullForUpdate(anyList())).thenReturn(matchedUsers);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(defaultRequest()))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_MEMBER_NOT_FOUND.getCode());
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenAnyMemberAlreadyBelongsToActiveTeam() {
		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNullForUpdate(anyList())).thenReturn(matchedUsers);
		when(projectGroupMemberRepository.existsByUser_IdInAndProjectGroup_Status(anyList(), eq(ProjectGroupStatus.ACTIVE)))
			.thenReturn(true);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(defaultRequest()))
			.isInstanceOf(ApplicationException.class)
			.hasMessage("이미 ACTIVE 팀에 속한 사용자가 포함되어 있습니다.");
		verify(projectGroupRepository, never()).save(any(ProjectGroup.class));
	}

	@Test
	void createProjectGroup_checksMembershipAgainstActiveStatusOnly() {
		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNullForUpdate(anyList())).thenReturn(matchedUsers);
		when(projectGroupMemberRepository.existsByUser_IdInAndProjectGroup_Status(anyList(), eq(ProjectGroupStatus.ACTIVE)))
			.thenReturn(false);
		when(projectGroupRepository.save(any(ProjectGroup.class))).thenAnswer(invocation -> {
			ProjectGroup saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 95L);
			return saved;
		});

		CreateProjectGroupResponse response = projectGroupService.createProjectGroup(defaultRequest());

		assertThat(response.projectGroupId()).isEqualTo(95L);
		verify(projectGroupMemberRepository)
			.existsByUser_IdInAndProjectGroup_Status(anyList(), eq(ProjectGroupStatus.ACTIVE));
	}

	@Test
	void grantAdminPermission_grantsAdmin_whenRequesterIsHost() {
		ProjectGroup projectGroup = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
		ProjectGroupMember hostMember = new ProjectGroupMember(projectGroup, mockUser(1L), MemberRole.BACKEND, GroupRole.HOST);
		ProjectGroupMember targetMember = new ProjectGroupMember(projectGroup, mockUser(2L), MemberRole.FRONTEND, GroupRole.MEMBER);
		assertThat(targetMember.isAdmin()).isFalse();

		when(projectGroupMemberRepository.findByProjectGroup_IdAndGroupRole(10L, GroupRole.HOST))
			.thenReturn(Optional.of(hostMember));
		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 2L))
			.thenReturn(Optional.of(targetMember));

		projectGroupService.grantAdminPermission(10L, 1L, 2L);

		assertThat(targetMember.isAdmin()).isTrue();
	}

	@Test
	void grantAdminPermission_throwsForbidden_whenRequesterIsNotHost() {
		ProjectGroup projectGroup = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
		ProjectGroupMember hostMember = new ProjectGroupMember(projectGroup, mockUser(1L), MemberRole.BACKEND, GroupRole.HOST);
		when(projectGroupMemberRepository.findByProjectGroup_IdAndGroupRole(20L, GroupRole.HOST))
			.thenReturn(Optional.of(hostMember));

		assertThatThrownBy(() -> projectGroupService.grantAdminPermission(20L, 99L, 2L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_PERMISSION_DENIED.getCode());
	}

	@Test
	void revokeAdminPermission_throwsForbidden_whenTargetIsHost() {
		ProjectGroup projectGroup = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
		ProjectGroupMember hostMember = new ProjectGroupMember(projectGroup, mockUser(1L), MemberRole.BACKEND, GroupRole.HOST);
		when(projectGroupMemberRepository.findByProjectGroup_IdAndGroupRole(30L, GroupRole.HOST))
			.thenReturn(Optional.of(hostMember));
		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(30L, 1L))
			.thenReturn(Optional.of(hostMember));

		assertThatThrownBy(() -> projectGroupService.revokeAdminPermission(30L, 1L, 1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_PERMISSION_DENIED.getCode());
	}

	@Test
	void revokeAdminPermission_revokesAdmin_whenRequesterIsHost() {
		ProjectGroup projectGroup = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
		ProjectGroupMember hostMember = new ProjectGroupMember(projectGroup, mockUser(1L), MemberRole.BACKEND, GroupRole.HOST);
		ProjectGroupMember targetMember = new ProjectGroupMember(projectGroup, mockUser(2L), MemberRole.FRONTEND, GroupRole.MEMBER);
		targetMember.grantAdmin();
		assertThat(targetMember.isAdmin()).isTrue();

		when(projectGroupMemberRepository.findByProjectGroup_IdAndGroupRole(40L, GroupRole.HOST))
			.thenReturn(Optional.of(hostMember));
		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(40L, 2L))
			.thenReturn(Optional.of(targetMember));

		projectGroupService.revokeAdminPermission(40L, 1L, 2L);

		assertThat(targetMember.isAdmin()).isFalse();
	}

	private CreateProjectGroupRequest defaultRequest() {
		return new CreateProjectGroupRequest(
			defaultMembers(),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP"
		);
	}

	private List<CreateProjectGroupMemberRequest> defaultMembers() {
		return List.of(
			new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND, GroupRole.HOST),
			new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND, GroupRole.MEMBER),
			new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN, GroupRole.MEMBER),
			new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND, GroupRole.MEMBER)
		);
	}

	private Users mockUser(Long userId) {
		Users user = Users.builder()
			.email("user" + userId + "@example.com")
			.password("encoded")
			.nickname("user" + userId)
			.level(1)
			.temperature(50)
			.build();
		ReflectionTestUtils.setField(user, "id", userId);
		return user;
	}
}
