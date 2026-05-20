package team.po.feature.teamspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.common.redis.RedisService;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.teamspace.dto.CreateGithubAppInstallationUrlResponse;
import team.po.feature.teamspace.domain.GithubInstallation;
import team.po.feature.teamspace.domain.ProjectGroupGithubInstallation;
import team.po.feature.teamspace.dto.GetGithubInstallationStatusResponse;
import team.po.feature.teamspace.repository.ProjectGroupGithubInstallationRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubRepositoryRepository;
import team.po.feature.user.domain.Users;

@ExtendWith(MockitoExtension.class)
class TeamspaceServiceTest {

	@Mock
	private ProjectGroupMemberRepository projectGroupMemberRepository;

	@Mock
	private ProjectGroupGithubInstallationRepository projectGroupGithubInstallationRepository;

	@Mock
	private ProjectGroupGithubRepositoryRepository projectGroupGithubRepository;

	@Mock
	private RedisService redisService;

	@InjectMocks
	private TeamspaceService teamspaceService;

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(teamspaceService, "githubAppSlug", "teampo");
		ReflectionTestUtils.setField(teamspaceService, "githubAppInstallationStateTtl", Duration.ofMinutes(5));
	}

	@Test
	void getGithubInstallationStatus_returnsDisconnected_whenGithubInstallationIsNotConnected() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(true);
		when(projectGroupGithubInstallationRepository.findByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(Optional.empty());

		GetGithubInstallationStatusResponse response = teamspaceService.getGithubInstallationStatus(10L, 1L);

		assertThat(response.connected()).isFalse();
		assertThat(response.organizationLogin()).isNull();
		assertThat(response.repositoryCount()).isZero();
		verify(projectGroupGithubRepository, never()).countByProjectGroup_IdAndDeletedAtIsNull(10L);
	}

	@Test
	void getGithubInstallationStatus_returnsConnectedStatus_whenGithubInstallationIsConnected() {
		ProjectGroupGithubInstallation githubConnection = ProjectGroupGithubInstallation.builder()
			.projectGroup(projectGroup())
			.githubInstallation(githubInstallation())
			.connectedBy(user())
			.build();

		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(true);
		when(projectGroupGithubInstallationRepository.findByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(Optional.of(githubConnection));
		when(projectGroupGithubRepository.countByProjectGroup_IdAndDeletedAtIsNull(10L)).thenReturn(2L);

		GetGithubInstallationStatusResponse response = teamspaceService.getGithubInstallationStatus(10L, 1L);

		assertThat(response.connected()).isTrue();
		assertThat(response.organizationLogin()).isEqualTo("student-team-org");
		assertThat(response.repositoryCount()).isEqualTo(2L);
	}

	@Test
	void getGithubInstallationStatus_throwsForbidden_whenRequesterIsNotProjectGroupMember() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(10L, 99L)).thenReturn(false);

		assertThatThrownBy(() -> teamspaceService.getGithubInstallationStatus(10L, 99L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_PERMISSION_DENIED.getCode());

		verify(projectGroupGithubInstallationRepository, never()).findByProjectGroup_IdAndDeletedAtIsNull(10L);
		verify(projectGroupGithubRepository, never()).countByProjectGroup_IdAndDeletedAtIsNull(10L);
	}

	@Test
	void createGithubAppInstallationUrl_savesStateAndReturnsGithubInstallUrl() {
		Users requester = user();
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_IdAndGroupRole(10L, 1L, GroupRole.HOST))
			.thenReturn(true);
		when(projectGroupGithubInstallationRepository.existsByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(false);

		CreateGithubAppInstallationUrlResponse response = teamspaceService.createGithubAppInstallationUrl(requester, 10L);

		assertThat(response.installUrl())
			.startsWith("https://github.com/apps/teampo/installations/new?state=");

		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
		verify(redisService).setValue(
			keyCaptor.capture(),
			payloadCaptor.capture(),
			eq(Duration.ofMinutes(5))
		);

		String state = response.installUrl().substring(response.installUrl().indexOf("state=") + "state=".length());
		assertThat(keyCaptor.getValue()).isEqualTo("github-app-install-state:" + state);
		assertThat(payloadCaptor.getValue()).startsWith("10:1:");
		assertThat(payloadCaptor.getValue()).endsWith(":" + state);
	}

	@Test
	void createGithubAppInstallationUrl_throwsConflict_whenGithubInstallationAlreadyExists() {
		Users requester = user();
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_IdAndGroupRole(10L, 1L, GroupRole.HOST))
			.thenReturn(true);
		when(projectGroupGithubInstallationRepository.existsByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(true);

		assertThatThrownBy(() -> teamspaceService.createGithubAppInstallationUrl(requester, 10L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.GITHUB_APP_INSTALLATION_ALREADY_EXISTS.getCode());

		verify(redisService, never()).setValue(anyString(), anyString(), eq(Duration.ofMinutes(5)));
	}

	private ProjectGroup projectGroup() {
		return ProjectGroup.builder()
			.projectName("TeamPo")
			.projectTitle("TeamPo")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
	}

	private GithubInstallation githubInstallation() {
		return GithubInstallation.builder()
			.installationId(12345L)
			.accountId(98765L)
			.accountLogin("student-team-org")
			.accountType(GithubInstallation.ORGANIZATION_ACCOUNT_TYPE)
			.build();
	}

	private Users user() {
		Users user = Users.builder()
			.email("tester@example.com")
			.nickname("tester")
			.level(1)
			.temperature(50)
			.build();
		ReflectionTestUtils.setField(user, "id", 1L);
		return user;
	}
}
