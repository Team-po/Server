package team.po.feature.teamspace.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.user.domain.Users;

@ExtendWith(MockitoExtension.class)
class WeeklyGithubSummarySchedulerTest {

	@Mock
	private ProjectGroupMemberRepository projectGroupMemberRepository;

	@Mock
	private TeamspaceService teamspaceService;

	private WeeklyGithubSummaryScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new WeeklyGithubSummaryScheduler(
			projectGroupMemberRepository,
			teamspaceService,
			List.of(Duration.ZERO, Duration.ZERO),
			duration -> {
			}
		);
	}

	@Test
	void generateWeeklyGithubSummaries_generatesSummaryForEachTargetMember() {
		ProjectGroupMember firstMember = projectGroupMember(100L, 10L, 1L);
		ProjectGroupMember secondMember = projectGroupMember(200L, 10L, 2L);
		when(projectGroupMemberRepository.findWeeklyGithubSummaryTargetMembers(ProjectGroupStatus.ACTIVE))
			.thenReturn(List.of(firstMember, secondMember));

		scheduler.generateWeeklyGithubSummaries();

		verify(teamspaceService).generateWeeklyGithubSummary(firstMember.getUser(), 10L);
		verify(teamspaceService).generateWeeklyGithubSummary(secondMember.getUser(), 10L);
	}

	@Test
	void generateWeeklyGithubSummaries_retriesRetryableFailure() {
		ProjectGroupMember firstMember = projectGroupMember(100L, 10L, 1L);
		when(projectGroupMemberRepository.findWeeklyGithubSummaryTargetMembers(ProjectGroupStatus.ACTIVE))
			.thenReturn(List.of(firstMember));
		when(teamspaceService.generateWeeklyGithubSummary(firstMember.getUser(), 10L))
			.thenThrow(new ApplicationException(ErrorCode.GEMINI_API_ERROR))
			.thenReturn(null);

		scheduler.generateWeeklyGithubSummaries();

		verify(teamspaceService, times(2)).generateWeeklyGithubSummary(firstMember.getUser(), 10L);
	}

	@Test
	void generateWeeklyGithubSummaries_retriesRetryableFailureUpToMaxAttempts() {
		ProjectGroupMember firstMember = projectGroupMember(100L, 10L, 1L);
		when(projectGroupMemberRepository.findWeeklyGithubSummaryTargetMembers(ProjectGroupStatus.ACTIVE))
			.thenReturn(List.of(firstMember));
		doThrow(new ApplicationException(ErrorCode.GEMINI_API_ERROR))
			.when(teamspaceService)
			.generateWeeklyGithubSummary(firstMember.getUser(), 10L);

		scheduler.generateWeeklyGithubSummaries();

		verify(teamspaceService, times(3)).generateWeeklyGithubSummary(firstMember.getUser(), 10L);
	}

	@Test
	void generateWeeklyGithubSummaries_continuesWhenOneMemberFails() {
		ProjectGroupMember firstMember = projectGroupMember(100L, 10L, 1L);
		ProjectGroupMember secondMember = projectGroupMember(200L, 10L, 2L);
		when(projectGroupMemberRepository.findWeeklyGithubSummaryTargetMembers(ProjectGroupStatus.ACTIVE))
			.thenReturn(List.of(firstMember, secondMember));
		doThrow(new ApplicationException(ErrorCode.GITHUB_ACCOUNT_NOT_LINKED))
			.when(teamspaceService)
			.generateWeeklyGithubSummary(firstMember.getUser(), 10L);

		scheduler.generateWeeklyGithubSummaries();

		verify(teamspaceService, times(1)).generateWeeklyGithubSummary(firstMember.getUser(), 10L);
		verify(teamspaceService).generateWeeklyGithubSummary(secondMember.getUser(), 10L);
	}

	private ProjectGroupMember projectGroupMember(Long projectGroupMemberId, Long projectGroupId, Long userId) {
		Users user = Users.builder()
			.email("member-%d@example.com".formatted(userId))
			.nickname("member-%d".formatted(userId))
			.level(1)
			.temperature(50)
			.build();
		ReflectionTestUtils.setField(user, "id", userId);

		ProjectGroup projectGroup = ProjectGroup.builder()
			.projectName("TeamPo")
			.projectTitle("TeamPo")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
		ReflectionTestUtils.setField(projectGroup, "id", projectGroupId);

		ProjectGroupMember projectGroupMember = ProjectGroupMember.builder()
			.projectGroup(projectGroup)
			.user(user)
			.memberRole(MemberRole.BACKEND)
			.groupRole(GroupRole.MEMBER)
			.build();
		ReflectionTestUtils.setField(projectGroupMember, "id", projectGroupMemberId);
		return projectGroupMember;
	}
}
