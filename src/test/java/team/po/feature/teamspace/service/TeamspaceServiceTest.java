package team.po.feature.teamspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import team.po.common.redis.RedisService;
import team.po.config.GithubAppProperties;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;
import team.po.feature.teamspace.dto.CompleteGithubAppInstallationRequest;
import team.po.feature.teamspace.dto.CreateGithubAppInstallationUrlResponse;
import team.po.feature.teamspace.domain.GithubInstallation;
import team.po.feature.teamspace.domain.ProjectGroupGithubInstallation;
import team.po.feature.teamspace.dto.GetGithubInstallationStatusResponse;
import team.po.feature.teamspace.repository.GithubInstallationRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubInstallationRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubRepositoryRepository;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class TeamspaceServiceTest {

	@Mock
	private ProjectGroupMemberRepository projectGroupMemberRepository;

	@Mock
	private ProjectGroupGithubInstallationRepository projectGroupGithubInstallationRepository;

	@Mock
	private ProjectGroupGithubRepositoryRepository projectGroupGithubRepositoryRepository;

	@Mock
	private GithubInstallationRepository githubInstallationRepository;

	@Mock
	private ProjectGroupRepository projectGroupRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private RedisService redisService;

	@Mock
	private GithubAppClient githubAppClient;

	private TeamspaceService teamspaceService;

	@BeforeEach
	void setUp() {
		GithubAppProperties githubAppProperties = new GithubAppProperties(
			12345L,
			"teampo",
			"test-private-key",
			Duration.ofMinutes(5),
			"https://api.github.com"
		);
		teamspaceService = new TeamspaceService(
			projectGroupMemberRepository,
			projectGroupGithubInstallationRepository,
			projectGroupGithubRepositoryRepository,
			githubInstallationRepository,
			projectGroupRepository,
			userRepository,
			redisService,
			githubAppProperties,
			githubAppClient
		);
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
		verify(projectGroupGithubRepositoryRepository, never()).countByProjectGroup_IdAndDeletedAtIsNull(10L);
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
		when(projectGroupGithubRepositoryRepository.countByProjectGroup_IdAndDeletedAtIsNull(10L)).thenReturn(2L);

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
		verify(projectGroupGithubRepositoryRepository, never()).countByProjectGroup_IdAndDeletedAtIsNull(10L);
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
		assertThat(payloadCaptor.getValue()).startsWith("10|1|");
		assertThat(payloadCaptor.getValue()).endsWith("|" + state);
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

	@Test
	void completeGithubAppInstallation_consumesState_whenRequestIsValid() {
		CompleteGithubAppInstallationRequest request = new CompleteGithubAppInstallationRequest(
			12345L,
			"install",
			"test-state"
		);
		when(redisService.getAndDeleteStringValue("github-app-install-state:test-state"))
			.thenReturn("10|1|2026-05-20T00:00:00Z|test-state");
		when(githubAppClient.getInstallation(12345L))
			.thenReturn(organizationInstallationInfo());
		GithubInstallation savedGithubInstallation = githubInstallation();
		when(projectGroupGithubInstallationRepository.existsByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(false);
		when(githubInstallationRepository.findByInstallationIdAndDeletedAtIsNull(12345L))
			.thenReturn(Optional.empty());
		when(githubInstallationRepository.save(any(GithubInstallation.class))).thenReturn(savedGithubInstallation);
		when(projectGroupRepository.findById(10L)).thenReturn(Optional.of(projectGroup()));
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user()));

		teamspaceService.completeGithubAppInstallation(request, 10L, 1L);

		verify(redisService).getAndDeleteStringValue("github-app-install-state:test-state");
		verify(githubAppClient).getInstallation(12345L);
		verify(githubInstallationRepository).save(any(GithubInstallation.class));
		verify(projectGroupGithubInstallationRepository).save(any(ProjectGroupGithubInstallation.class));
	}

	@Test
	void completeGithubAppInstallation_throwsBadRequest_whenInstallationAccountIsNotOrganization() {
		CompleteGithubAppInstallationRequest request = new CompleteGithubAppInstallationRequest(
			12345L,
			"install",
			"test-state"
		);
		when(redisService.getAndDeleteStringValue("github-app-install-state:test-state"))
			.thenReturn("10|1|2026-05-20T00:00:00Z|test-state");
		when(githubAppClient.getInstallation(12345L))
			.thenReturn(new GithubAppClient.GithubAppInstallationInfo(
				12345L,
				98765L,
				"personal-account",
				"User"
			));

		assertThatThrownBy(() -> teamspaceService.completeGithubAppInstallation(request, 10L, 1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.INVALID_GITHUB_APP_INSTALLATION_ACCOUNT.getCode());
	}

	@Test
	void completeGithubAppInstallation_updatesGithubInstallation_whenInstallationAlreadyExists() {
		CompleteGithubAppInstallationRequest request = new CompleteGithubAppInstallationRequest(
			12345L,
			"install",
			"test-state"
		);
		GithubInstallation githubInstallation = GithubInstallation.builder()
			.installationId(12345L)
			.accountId(11111L)
			.accountLogin("old-org")
			.accountType(GithubInstallation.ORGANIZATION_ACCOUNT_TYPE)
			.build();
		when(redisService.getAndDeleteStringValue("github-app-install-state:test-state"))
			.thenReturn("10|1|2026-05-20T00:00:00Z|test-state");
		when(githubAppClient.getInstallation(12345L))
			.thenReturn(organizationInstallationInfo());
		when(projectGroupGithubInstallationRepository.existsByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(false);
		when(githubInstallationRepository.findByInstallationIdAndDeletedAtIsNull(12345L))
			.thenReturn(Optional.of(githubInstallation));
		when(projectGroupRepository.findById(10L)).thenReturn(Optional.of(projectGroup()));
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user()));

		teamspaceService.completeGithubAppInstallation(request, 10L, 1L);

		assertThat(githubInstallation.getAccountId()).isEqualTo(98765L);
		assertThat(githubInstallation.getAccountLogin()).isEqualTo("student-team-org");
		verify(githubInstallationRepository, never()).save(any(GithubInstallation.class));
		verify(projectGroupGithubInstallationRepository).save(any(ProjectGroupGithubInstallation.class));
	}

	@Test
	void completeGithubAppInstallation_throwsConflict_whenProjectGroupAlreadyConnected() {
		CompleteGithubAppInstallationRequest request = new CompleteGithubAppInstallationRequest(
			12345L,
			"install",
			"test-state"
		);
		when(redisService.getAndDeleteStringValue("github-app-install-state:test-state"))
			.thenReturn("10|1|2026-05-20T00:00:00Z|test-state");
		when(githubAppClient.getInstallation(12345L))
			.thenReturn(organizationInstallationInfo());
		when(projectGroupGithubInstallationRepository.existsByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(true);

		assertThatThrownBy(() -> teamspaceService.completeGithubAppInstallation(request, 10L, 1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.GITHUB_APP_INSTALLATION_ALREADY_EXISTS.getCode());

		verify(githubInstallationRepository, never()).save(any(GithubInstallation.class));
		verify(projectGroupGithubInstallationRepository, never()).save(any(ProjectGroupGithubInstallation.class));
	}

	@Test
	void completeGithubAppInstallation_throwsBadRequest_whenStateIsExpired() {
		CompleteGithubAppInstallationRequest request = new CompleteGithubAppInstallationRequest(
			12345L,
			"install",
			"expired-state"
		);
		when(redisService.getAndDeleteStringValue("github-app-install-state:expired-state")).thenReturn(null);

		assertThatThrownBy(() -> teamspaceService.completeGithubAppInstallation(request, 10L, 1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.INVALID_GITHUB_APP_INSTALLATION_STATE.getCode());
	}

	@Test
	void completeGithubAppInstallation_throwsBadRequest_whenProjectGroupDoesNotMatchState() {
		CompleteGithubAppInstallationRequest request = new CompleteGithubAppInstallationRequest(
			12345L,
			"install",
			"test-state"
		);
		when(redisService.getAndDeleteStringValue("github-app-install-state:test-state"))
			.thenReturn("99|1|2026-05-20T00:00:00Z|test-state");

		assertThatThrownBy(() -> teamspaceService.completeGithubAppInstallation(request, 10L, 1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.INVALID_GITHUB_APP_INSTALLATION_STATE.getCode());
	}

	@Test
	void completeGithubAppInstallation_throwsBadRequest_whenRequesterDoesNotMatchState() {
		CompleteGithubAppInstallationRequest request = new CompleteGithubAppInstallationRequest(
			12345L,
			"install",
			"test-state"
		);
		when(redisService.getAndDeleteStringValue("github-app-install-state:test-state"))
			.thenReturn("10|99|2026-05-20T00:00:00Z|test-state");

		assertThatThrownBy(() -> teamspaceService.completeGithubAppInstallation(request, 10L, 1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.INVALID_GITHUB_APP_INSTALLATION_STATE.getCode());
	}

	@Test
	void completeGithubAppInstallation_throwsBadRequest_whenSetupActionIsInvalid() {
		CompleteGithubAppInstallationRequest request = new CompleteGithubAppInstallationRequest(
			12345L,
			"update",
			"test-state"
		);

		assertThatThrownBy(() -> teamspaceService.completeGithubAppInstallation(request, 10L, 1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.INVALID_GITHUB_APP_SETUP_ACTION.getCode());

		verify(redisService, never()).getAndDeleteStringValue(anyString());
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

	private GithubAppClient.GithubAppInstallationInfo organizationInstallationInfo() {
		return new GithubAppClient.GithubAppInstallationInfo(
			12345L,
			98765L,
			"student-team-org",
			GithubInstallation.ORGANIZATION_ACCOUNT_TYPE
		);
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
