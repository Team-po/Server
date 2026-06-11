package team.po.feature.teamrule.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.MemberRole;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;
import team.po.feature.teamrule.domain.ProjectTeamRule;
import team.po.feature.teamrule.domain.TeamRuleTemplate;
import team.po.feature.teamrule.dto.TeamRuleResponse;
import team.po.feature.teamrule.dto.UpdateTeamRuleRequest;
import team.po.feature.teamrule.repository.ProjectTeamRuleRepository;
import team.po.feature.user.domain.Users;

@ExtendWith(MockitoExtension.class)
class TeamRuleServiceTest {

	@Mock
	private ProjectTeamRuleRepository projectTeamRuleRepository;

	@Mock
	private ProjectGroupMemberRepository projectGroupMemberRepository;

	@Mock
	private ProjectGroupRepository projectGroupRepository;

	private TeamRuleService teamRuleService;

	@BeforeEach
	void setUp() {
		teamRuleService = new TeamRuleService(
			projectTeamRuleRepository,
			projectGroupMemberRepository,
			projectGroupRepository
		);
	}

	@Test
	void getTeamRule_returnsExistingRule_whenRequesterIsMember() {
		Users requester = mockUser(1L, "tester");
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);
		ProjectTeamRule teamRule = mockTeamRule(100L, projectGroup, requester, "# 팀 룰", 3L);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(Optional.of(member));
		when(projectTeamRuleRepository.findByProjectGroup_Id(10L)).thenReturn(Optional.of(teamRule));

		TeamRuleResponse response = teamRuleService.getTeamRule(10L, requester);

		assertThat(response.id()).isEqualTo(100L);
		assertThat(response.content()).isEqualTo("# 팀 룰");
		assertThat(response.version()).isEqualTo(3L);
		verify(projectGroupRepository, never()).findByIdForUpdate(any());
	}

	@Test
	void getTeamRule_createsDefaultRule_whenRuleDoesNotExist() {
		Users requester = mockUser(1L, "tester");
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(Optional.of(member));
		when(projectTeamRuleRepository.findByProjectGroup_Id(10L))
			.thenReturn(Optional.empty())
			.thenReturn(Optional.empty());
		when(projectGroupRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(projectGroup));
		when(projectTeamRuleRepository.saveAndFlush(any(ProjectTeamRule.class))).thenAnswer(invocation -> {
			ProjectTeamRule saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 100L);
			ReflectionTestUtils.setField(saved, "version", 0L);
			ReflectionTestUtils.setField(saved, "createdAt", Instant.parse("2026-06-10T10:00:00Z"));
			ReflectionTestUtils.setField(saved, "updatedAt", Instant.parse("2026-06-10T10:00:00Z"));
			return saved;
		});

		TeamRuleResponse response = teamRuleService.getTeamRule(10L, requester);

		assertThat(response.content()).isEqualTo(TeamRuleTemplate.DEFAULT_CONTENT);
		assertThat(response.version()).isZero();
		assertThat(response.updatedByNickname()).isEqualTo("tester");
	}

	@Test
	void updateTeamRule_updatesContent_whenVersionMatches() {
		Users requester = mockUser(1L, "tester");
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);
		ProjectTeamRule teamRule = mockTeamRule(100L, projectGroup, requester, "# 기존 팀 룰", 3L);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(Optional.of(member));
		when(projectTeamRuleRepository.findByProjectGroup_Id(10L)).thenReturn(Optional.of(teamRule));

		TeamRuleResponse response = teamRuleService.updateTeamRule(
			10L,
			requester,
			new UpdateTeamRuleRequest("# 수정된 팀 룰", 3L)
		);

		assertThat(response.content()).isEqualTo("# 수정된 팀 룰");
		assertThat(teamRule.getUpdatedBy().getId()).isEqualTo(1L);
		verify(projectTeamRuleRepository).flush();
	}

	@Test
	void updateTeamRule_throwsConflict_whenRequestVersionIsStale() {
		Users requester = mockUser(1L, "tester");
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);
		ProjectTeamRule teamRule = mockTeamRule(100L, projectGroup, requester, "# 기존 팀 룰", 3L);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(Optional.of(member));
		when(projectTeamRuleRepository.findByProjectGroup_Id(10L)).thenReturn(Optional.of(teamRule));

		assertThatThrownBy(() -> teamRuleService.updateTeamRule(
			10L,
			requester,
			new UpdateTeamRuleRequest("# 수정된 팀 룰", 2L)
		))
			.isInstanceOf(ApplicationException.class)
			.extracting(exception -> ((ApplicationException)exception).getErrorCode())
			.isEqualTo(ErrorCode.PROJECT_TEAM_RULE_UPDATE_CONFLICT);

		assertThat(teamRule.getContent()).isEqualTo("# 기존 팀 룰");
		verify(projectTeamRuleRepository, never()).flush();
	}

	@Test
	void updateTeamRule_throwsConflict_whenJpaOptimisticLockFails() {
		Users requester = mockUser(1L, "tester");
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);
		ProjectTeamRule teamRule = mockTeamRule(100L, projectGroup, requester, "# 기존 팀 룰", 3L);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(Optional.of(member));
		when(projectTeamRuleRepository.findByProjectGroup_Id(10L)).thenReturn(Optional.of(teamRule));
		doThrow(new ObjectOptimisticLockingFailureException(ProjectTeamRule.class, 100L))
			.when(projectTeamRuleRepository).flush();

		assertThatThrownBy(() -> teamRuleService.updateTeamRule(
			10L,
			requester,
			new UpdateTeamRuleRequest("# 수정된 팀 룰", 3L)
		))
			.isInstanceOf(ApplicationException.class)
			.extracting(exception -> ((ApplicationException)exception).getErrorCode())
			.isEqualTo(ErrorCode.PROJECT_TEAM_RULE_UPDATE_CONFLICT);
	}

	@Test
	void updateTeamRule_throwsForbidden_whenProjectGroupFinished() {
		Users requester = mockUser(1L, "tester");
		ProjectGroup projectGroup = mockProjectGroup(10L, ProjectGroupStatus.FINISHED);
		ProjectGroupMember member = new ProjectGroupMember(projectGroup, requester, MemberRole.BACKEND, GroupRole.MEMBER);

		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(Optional.of(member));

		assertThatThrownBy(() -> teamRuleService.updateTeamRule(
			10L,
			requester,
			new UpdateTeamRuleRequest("# 수정된 팀 룰", 3L)
		))
			.isInstanceOf(ApplicationException.class)
			.extracting(exception -> ((ApplicationException)exception).getErrorCode())
			.isEqualTo(ErrorCode.PROJECT_TEAM_RULE_WRITE_NOT_ALLOWED);

		verify(projectTeamRuleRepository, never()).findByProjectGroup_Id(any());
	}

	@Test
	void getTeamRule_throwsForbidden_whenRequesterIsNotMember() {
		Users requester = mockUser(1L, "tester");
		when(projectGroupMemberRepository.findByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> teamRuleService.getTeamRule(10L, requester))
			.isInstanceOf(ApplicationException.class)
			.extracting(exception -> ((ApplicationException)exception).getErrorCode())
			.isEqualTo(ErrorCode.PROJECT_GROUP_ACCESS_DENIED);

		verify(projectTeamRuleRepository, never()).findByProjectGroup_Id(any());
	}

	private ProjectTeamRule mockTeamRule(
		Long id,
		ProjectGroup projectGroup,
		Users user,
		String content,
		Long version
	) {
		ProjectTeamRule teamRule = ProjectTeamRule.createDefault(projectGroup, user);
		teamRule.update(content, user);
		ReflectionTestUtils.setField(teamRule, "id", id);
		ReflectionTestUtils.setField(teamRule, "version", version);
		ReflectionTestUtils.setField(teamRule, "createdAt", Instant.parse("2026-06-10T10:00:00Z"));
		ReflectionTestUtils.setField(teamRule, "updatedAt", Instant.parse("2026-06-10T10:00:00Z"));
		return teamRule;
	}

	private ProjectGroup mockProjectGroup(Long projectGroupId, ProjectGroupStatus status) {
		ProjectGroup projectGroup = ProjectGroup.builder()
			.projectName("Teampo Alpha")
			.projectTitle("주제 A")
			.projectDescription("설명")
			.projectMvp("MVP")
			.status(status)
			.build();
		ReflectionTestUtils.setField(projectGroup, "id", projectGroupId);
		return projectGroup;
	}

	private Users mockUser(Long userId, String nickname) {
		Users user = Users.builder()
			.email("user" + userId + "@example.com")
			.password("encoded")
			.nickname(nickname)
			.level(1)
			.temperature(50)
			.build();
		ReflectionTestUtils.setField(user, "id", userId);
		return user;
	}
}
