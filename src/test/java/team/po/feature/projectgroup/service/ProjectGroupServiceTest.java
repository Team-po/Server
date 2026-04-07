package team.po.feature.projectgroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
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
		lenient().when(projectGroupRepository.findByMatchId(anyLong())).thenReturn(Optional.empty());
		lenient().when(projectGroupMemberRepository.existsByUser_IdIn(anyList())).thenReturn(false);
	}

	@Test
	void createProjectGroup_savesGroupAndMembers_whenRequestIsValid() {
			CreateProjectGroupRequest request = new CreateProjectGroupRequest(
				1L,
				1001L,
				List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP",
			ProjectGroupStatus.ACTIVE
		);

		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(matchedUsers);
		when(projectGroupRepository.save(any(ProjectGroup.class))).thenAnswer(invocation -> {
			ProjectGroup saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 10L);
			return saved;
		});
		CreateProjectGroupResponse response = projectGroupService.createProjectGroup(1L, request);

		assertThat(response.groupId()).isEqualTo(10L);
		assertThat(response.projectName()).isEqualTo("Teampo Alpha");
		assertThat(response.status()).isEqualTo("ACTIVE");
		assertThat(response.memberCount()).isEqualTo(4);

		ArgumentCaptor<List<ProjectGroupMember>> membersCaptor = ArgumentCaptor.forClass(List.class);
		verify(projectGroupMemberRepository).saveAllAndFlush(membersCaptor.capture());
		List<ProjectGroupMember> members = membersCaptor.getValue();
		assertThat(members).hasSize(4);
		assertThat(members.stream().filter(member -> member.getGroupRole().name().equals("HOST")).count()).isEqualTo(1L);
		assertThat(members.stream().filter(member -> member.getMemberRole().name().equals("BACKEND")).count()).isEqualTo(2L);
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenMemberUserIdsContainDuplicate() {
			CreateProjectGroupRequest request = new CreateProjectGroupRequest(
				1L,
				1002L,
				List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND),
				new CreateProjectGroupMemberRequest(1L, MemberRole.FRONTEND),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP",
			ProjectGroupStatus.ACTIVE
		);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(1L, request))
			.isInstanceOf(ProjectGroupException.class)
			.extracting("error")
			.isEqualTo(ErrorCodeConstants.INVALID_PROJECT_GROUP_REQUEST);
	}

	@Test
	void createProjectGroup_throwsForbidden_whenRequesterIsNotHost() {
			CreateProjectGroupRequest request = new CreateProjectGroupRequest(
				1L,
				1003L,
				List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP",
			ProjectGroupStatus.ACTIVE
		);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(99L, request))
			.isInstanceOf(ProjectGroupException.class)
			.extracting("error")
			.isEqualTo(ErrorCodeConstants.PROJECT_GROUP_PERMISSION_DENIED);
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenHostIsNotInMemberList() {
			CreateProjectGroupRequest request = new CreateProjectGroupRequest(
				99L,
				1004L,
				List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP",
			ProjectGroupStatus.ACTIVE
		);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(99L, request))
			.isInstanceOf(ProjectGroupException.class)
			.extracting("error")
			.isEqualTo(ErrorCodeConstants.INVALID_PROJECT_GROUP_REQUEST);
	}

	@Test
	void createProjectGroup_throwsNotFound_whenAnyMemberUserDoesNotExist() {
			CreateProjectGroupRequest request = new CreateProjectGroupRequest(
				1L,
				1005L,
				List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP",
			ProjectGroupStatus.ACTIVE
		);
		List<Users> matchedUsers = List.of(
			org.mockito.Mockito.mock(Users.class),
			org.mockito.Mockito.mock(Users.class),
			org.mockito.Mockito.mock(Users.class)
		);
		when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(matchedUsers);
		assertThatThrownBy(() -> projectGroupService.createProjectGroup(1L, request))
			.isInstanceOf(ProjectGroupException.class)
			.extracting("error")
			.isEqualTo(ErrorCodeConstants.PROJECT_GROUP_MEMBER_NOT_FOUND);
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenMemberSizeIsNotFour() {
			CreateProjectGroupRequest request = new CreateProjectGroupRequest(
				1L,
				1006L,
				List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP",
			ProjectGroupStatus.ACTIVE
		);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(1L, request))
			.isInstanceOf(ProjectGroupException.class)
			.extracting("error")
			.isEqualTo(ErrorCodeConstants.INVALID_PROJECT_GROUP_REQUEST);
	}

	@Test
	void createProjectGroup_throwsForbidden_whenRequesterDoesNotMatchHost() {
			CreateProjectGroupRequest request = new CreateProjectGroupRequest(
				1L,
				1007L,
				List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP",
			ProjectGroupStatus.ACTIVE
		);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(2L, request))
			.isInstanceOf(ProjectGroupException.class)
			.extracting("error")
			.isEqualTo(ErrorCodeConstants.PROJECT_GROUP_PERMISSION_DENIED);
	}

	@Test
	void createProjectGroup_returnsExistingGroup_whenSameMatchIdRequestIsRetried() {
		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			1L,
			2001L,
			List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP",
			ProjectGroupStatus.ACTIVE
		);

		ProjectGroup existing = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.matchId(2001L)
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
		when(projectGroupRepository.findByMatchId(2001L)).thenReturn(Optional.of(existing));
		when(projectGroupMemberRepository.findAllByProjectGroup_Id(99L)).thenReturn(existingMembers);

		CreateProjectGroupResponse response = projectGroupService.createProjectGroup(1L, request);

		assertThat(response.groupId()).isEqualTo(99L);
		assertThat(response.memberCount()).isEqualTo(4);
		verify(projectGroupRepository, never()).save(any(ProjectGroup.class));
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenSameMatchIdHasDifferentProjectInfo() {
		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			1L,
			2002L,
			List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND)
			),
			"Teampo Alpha",
			"다른 제목",
			"설명",
			"MVP",
			ProjectGroupStatus.ACTIVE
		);

		ProjectGroup existing = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.matchId(2002L)
			.projectDescription("설명")
			.projectMvp("MVP")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
		ReflectionTestUtils.setField(existing, "id", 98L);

		List<ProjectGroupMember> existingMembers = List.of(
			new ProjectGroupMember(existing, mockUser(1L), MemberRole.BACKEND, GroupRole.HOST)
		);
		when(projectGroupRepository.findByMatchId(2002L)).thenReturn(Optional.of(existing));
		when(projectGroupMemberRepository.findAllByProjectGroup_Id(98L)).thenReturn(existingMembers);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(1L, request))
			.isInstanceOf(ProjectGroupException.class)
			.extracting("error")
			.isEqualTo(ErrorCodeConstants.INVALID_PROJECT_GROUP_REQUEST);
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenAnyMemberAlreadyBelongsToTeam() {
		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			1L,
			2003L,
			List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP",
			ProjectGroupStatus.ACTIVE
		);

		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(matchedUsers);
		when(projectGroupMemberRepository.existsByUser_IdIn(anyList())).thenReturn(true);

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(1L, request))
			.isInstanceOf(ProjectGroupException.class)
			.hasMessage("이미 팀에 속한 사용자가 포함되어 있습니다.");
		verify(projectGroupRepository, never()).save(any(ProjectGroup.class));
	}

	@Test
	void createProjectGroup_throwsBadRequest_whenConcurrentInsertCausesUserUniqueConflict() {
		CreateProjectGroupRequest request = new CreateProjectGroupRequest(
			1L,
			2004L,
			List.of(
				new CreateProjectGroupMemberRequest(1L, MemberRole.BACKEND),
				new CreateProjectGroupMemberRequest(2L, MemberRole.FRONTEND),
				new CreateProjectGroupMemberRequest(3L, MemberRole.DESIGN),
				new CreateProjectGroupMemberRequest(4L, MemberRole.BACKEND)
			),
			"Teampo Alpha",
			"주제 A",
			"설명",
			"MVP",
			ProjectGroupStatus.ACTIVE
		);

		List<Users> matchedUsers = List.of(mockUser(1L), mockUser(2L), mockUser(3L), mockUser(4L));
		when(userRepository.findAllByIdInAndDeletedAtIsNull(anyList())).thenReturn(matchedUsers);
		when(projectGroupMemberRepository.existsByUser_IdIn(anyList())).thenReturn(false, true);
		when(projectGroupRepository.save(any(ProjectGroup.class))).thenAnswer(invocation -> {
			ProjectGroup saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 97L);
			return saved;
		});
		doThrow(new DataIntegrityViolationException("duplicate user membership"))
			.when(projectGroupMemberRepository).saveAllAndFlush(anyList());

		assertThatThrownBy(() -> projectGroupService.createProjectGroup(1L, request))
			.isInstanceOf(ProjectGroupException.class)
			.hasMessage("이미 팀에 속한 사용자가 포함되어 있습니다.");
	}

	private Users mockUser(Long userId) {
		Users user = org.mockito.Mockito.mock(Users.class);
		lenient().when(user.getId()).thenReturn(userId);
		return user;
	}

}
