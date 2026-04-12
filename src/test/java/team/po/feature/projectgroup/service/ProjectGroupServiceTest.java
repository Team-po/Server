package team.po.feature.projectgroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.exception.ErrorCodeConstants;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.MemberRole;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.dto.CreateProjectGroupMemberRequest;
import team.po.feature.projectgroup.dto.CreateProjectGroupRequest;
import team.po.feature.projectgroup.dto.CreateProjectGroupResponse;
import team.po.feature.projectgroup.exception.ProjectGroupException;
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

	@BeforeEach
	void setUp() {
		lenient().when(projectGroupRepository.findByGroupId(anyLong())).thenReturn(Optional.empty());
		lenient().when(projectGroupMemberRepository.existsByUser_IdIn(anyList())).thenReturn(false);
	}

	@Test
	void createProjectGroup_savesGroupAndMembers_whenRequestIsValid() {
		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(matchedUsers);
		when(projectGroupRepository.save(any(ProjectGroup.class))).thenAnswer(invocation -> {
			ProjectGroup saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 10L);
			return saved;
		});

		CreateProjectGroupResponse response = projectGroupService.createProjectGroup(defaultRequest(1001L));

		assertThat(response.groupId()).isEqualTo(10L);
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
			1002L,
			List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND, GroupRole.HOST, true),
				new CreateProjectGroupMemberRequest(1L, MemberRole.FRONTEND, GroupRole.MEMBER, false),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN, GroupRole.MEMBER, false),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND, GroupRole.MEMBER, false)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP"
		);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(request))
			.isInstanceOf(ProjectGroupException.class)
			.extracting("error")
			.isEqualTo(ErrorCodeConstants.INVALID_PROJECT_GROUP_REQUEST);
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenMemberSizeIsNotFour() {
		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			1003L,
			List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND, GroupRole.HOST, true),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND, GroupRole.MEMBER, false),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN, GroupRole.MEMBER, false)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP"
		);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(request))
			.isInstanceOf(ProjectGroupException.class)
			.extracting("error")
			.isEqualTo(ErrorCodeConstants.INVALID_PROJECT_GROUP_REQUEST);
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenHostCountIsNotOne() {
		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			1004L,
			List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND, GroupRole.MEMBER, false),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND, GroupRole.MEMBER, false),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN, GroupRole.MEMBER, false),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND, GroupRole.MEMBER, false)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP"
		);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(request))
			.isInstanceOf(ProjectGroupException.class)
			.extracting("error")
			.isEqualTo(ErrorCodeConstants.INVALID_PROJECT_GROUP_REQUEST);
	}

	@Test
	void createProjectGroup_savesGroup_whenHostAdminIsFalse() {
		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(matchedUsers);
		when(projectGroupRepository.save(any(ProjectGroup.class))).thenAnswer(invocation -> {
			ProjectGroup saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 11L);
			return saved;
		});

		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			1005L,
			List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND, GroupRole.HOST, false),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND, GroupRole.MEMBER, false),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN, GroupRole.MEMBER, false),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND, GroupRole.MEMBER, false)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP"
		);

		CreateProjectGroupResponse response = projectGroupService.createProjectGroup(request);
		assertThat(response.groupId()).isEqualTo(11L);
		assertThat(response.memberCount()).isEqualTo(4);
	}

	@Test
	void createProjectGroup_throwsNotFound_whenAnyMemberUserDoesNotExist() {
		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L));
		when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(matchedUsers);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(defaultRequest(1006L)))
			.isInstanceOf(ProjectGroupException.class)
			.extracting("error")
			.isEqualTo(ErrorCodeConstants.PROJECT_GROUP_MEMBER_NOT_FOUND);
	}

	@Test
	void createProjectGroup_returnsExistingGroup_whenSameGroupIdRequestIsRetried() {
		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(matchedUsers);

		ProjectGroup existing = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.groupId(2001L)
			.projectDescription("설명")
			.projectMvp("MVP")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
		ReflectionTestUtils.setField(existing, "id", 99L);

		List<ProjectGroupMember> existingMembers = List.of(
			new ProjectGroupMember(existing, mockUser(1L), MemberRole.BACKEND, GroupRole.HOST),
			new ProjectGroupMember(existing, mockUser(2L), MemberRole.FRONTEND, GroupRole.MEMBER),
			new ProjectGroupMember(existing, mockUser(3L), MemberRole.DESIGN, GroupRole.MEMBER),
			new ProjectGroupMember(existing, mockUser(4L), MemberRole.BACKEND, GroupRole.MEMBER)
		);
		when(projectGroupRepository.save(any(ProjectGroup.class)))
			.thenThrow(new DataIntegrityViolationException("duplicate group id"));
		when(projectGroupRepository.findByGroupId(2001L)).thenReturn(Optional.of(existing));
		when(projectGroupMemberRepository.findAllByProjectGroup_Id(99L)).thenReturn(existingMembers);

		CreateProjectGroupResponse response = projectGroupService.createProjectGroup(defaultRequest(2001L));

		assertThat(response.groupId()).isEqualTo(99L);
		assertThat(response.memberCount()).isEqualTo(4);
	}

	@Test
	void createProjectGroup_returnsExistingGroup_whenSameGroupIdHasDifferentProjectInfo() {
		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(matchedUsers);

		ProjectGroup existing = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.groupId(2002L)
			.projectDescription("설명")
			.projectMvp("MVP")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
		ReflectionTestUtils.setField(existing, "id", 97L);
		List<ProjectGroupMember> existingMembers = List.of(
			new ProjectGroupMember(existing, mockUser(1L), MemberRole.BACKEND, GroupRole.HOST),
			new ProjectGroupMember(existing, mockUser(2L), MemberRole.FRONTEND, GroupRole.MEMBER),
			new ProjectGroupMember(existing, mockUser(3L), MemberRole.DESIGN, GroupRole.MEMBER),
			new ProjectGroupMember(existing, mockUser(4L), MemberRole.BACKEND, GroupRole.MEMBER)
		);
		when(projectGroupRepository.save(any(ProjectGroup.class)))
			.thenThrow(new DataIntegrityViolationException("duplicate group id"));
		when(projectGroupRepository.findByGroupId(2002L)).thenReturn(Optional.of(existing));
		when(projectGroupMemberRepository.findAllByProjectGroup_Id(97L)).thenReturn(existingMembers);

		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			2002L,
			defaultMembers(),
			"Teampo Alpha",
			"다른 제목",
			"설명",
			"MVP"
		);

		CreateProjectGroupResponse response = projectGroupService.createProjectGroup(request);
		assertThat(response.groupId()).isEqualTo(97L);
		assertThat(response.memberCount()).isEqualTo(4);
	}

	@Test
	void createProjectGroup_returnsExistingGroup_whenSameGroupIdHasDifferentMemberDefinition() {
		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(matchedUsers);

		ProjectGroup existing = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.groupId(2003L)
			.projectDescription("설명")
			.projectMvp("MVP")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
		ReflectionTestUtils.setField(existing, "id", 96L);
		List<ProjectGroupMember> existingMembers = List.of(
			new ProjectGroupMember(existing, mockUser(1L), MemberRole.BACKEND, GroupRole.HOST),
			new ProjectGroupMember(existing, mockUser(2L), MemberRole.FRONTEND, GroupRole.MEMBER),
			new ProjectGroupMember(existing, mockUser(3L), MemberRole.DESIGN, GroupRole.MEMBER),
			new ProjectGroupMember(existing, mockUser(4L), MemberRole.BACKEND, GroupRole.MEMBER)
		);
		when(projectGroupRepository.save(any(ProjectGroup.class)))
			.thenThrow(new DataIntegrityViolationException("duplicate group id"));
		when(projectGroupRepository.findByGroupId(2003L)).thenReturn(Optional.of(existing));
		when(projectGroupMemberRepository.findAllByProjectGroup_Id(96L)).thenReturn(existingMembers);

		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			2003L,
			List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND, GroupRole.MEMBER, false),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND, GroupRole.HOST, true),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN, GroupRole.MEMBER, false),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND, GroupRole.MEMBER, false)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP"
		);

		CreateProjectGroupResponse response = projectGroupService.createProjectGroup(request);
		assertThat(response.groupId()).isEqualTo(96L);
		assertThat(response.memberCount()).isEqualTo(4);
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenAnyMemberAlreadyBelongsToTeam() {
		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(matchedUsers);
		when(projectGroupMemberRepository.existsByUser_IdIn(anyList())).thenReturn(true);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(defaultRequest(2004L)))
			.isInstanceOf(ProjectGroupException.class)
			.hasMessage("이미 팀에 속한 사용자가 포함되어 있습니다.");
		verify(projectGroupRepository, never()).save(any(ProjectGroup.class));
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenConcurrentInsertCausesUserUniqueConflict() {
		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(matchedUsers);
		when(projectGroupMemberRepository.existsByUser_IdIn(anyList())).thenReturn(false, true);
		when(projectGroupRepository.save(any(ProjectGroup.class))).thenAnswer(invocation -> {
			ProjectGroup saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 95L);
			return saved;
		});
		doThrow(new DataIntegrityViolationException("duplicate user membership"))
			.when(projectGroupMemberRepository).saveAllAndFlush(anyList());

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(defaultRequest(2005L)))
			.isInstanceOf(ProjectGroupException.class)
			.hasMessage("이미 팀에 속한 사용자가 포함되어 있습니다.");
	}

	@Test
	void grantAdminPermission_grantsAdmin_whenRequesterIsHost() {
		ProjectGroup projectGroup = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.groupId(3001L)
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
			.groupId(3002L)
			.status(ProjectGroupStatus.ACTIVE)
			.build();
		ProjectGroupMember hostMember = new ProjectGroupMember(projectGroup, mockUser(1L), MemberRole.BACKEND, GroupRole.HOST);
		when(projectGroupMemberRepository.findByProjectGroup_IdAndGroupRole(20L, GroupRole.HOST))
			.thenReturn(Optional.of(hostMember));

		assertThatThrownBy(() -> projectGroupService.grantAdminPermission(20L, 99L, 2L))
			.isInstanceOf(ProjectGroupException.class)
			.extracting("error")
			.isEqualTo(ErrorCodeConstants.PROJECT_GROUP_PERMISSION_DENIED);
	}

	@Test
	void revokeAdminPermission_throwsForbidden_whenTargetIsHost() {
		ProjectGroup projectGroup = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.groupId(3003L)
			.status(ProjectGroupStatus.ACTIVE)
			.build();
		ProjectGroupMember hostMember = new ProjectGroupMember(projectGroup, mockUser(1L), MemberRole.BACKEND, GroupRole.HOST);
		when(projectGroupMemberRepository.findByProjectGroup_IdAndGroupRole(30L, GroupRole.HOST))
			.thenReturn(Optional.of(hostMember));
		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(30L, 1L))
			.thenReturn(Optional.of(hostMember));

		assertThatThrownBy(() -> projectGroupService.revokeAdminPermission(30L, 1L, 1L))
			.isInstanceOf(ProjectGroupException.class)
			.extracting("error")
			.isEqualTo(ErrorCodeConstants.PROJECT_GROUP_PERMISSION_DENIED);
	}

	@Test
	void revokeAdminPermission_revokesAdmin_whenRequesterIsHost() {
		ProjectGroup projectGroup = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.groupId(3004L)
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

	private CreateProjectGroupRequest defaultRequest(Long groupId) {
		return new CreateProjectGroupRequest(
			groupId,
			defaultMembers(),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP"
		);
	}

	private List<CreateProjectGroupMemberRequest> defaultMembers() {
		return List.of(
			new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND, GroupRole.HOST, true),
			new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND, GroupRole.MEMBER, false),
			new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN, GroupRole.MEMBER, false),
			new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND, GroupRole.MEMBER, false)
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
