package team.po.feature.teamspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
import team.po.feature.teamspace.domain.GithubInstallation;
import team.po.feature.teamspace.domain.ProjectGroupGithubInstallation;
import team.po.feature.teamspace.domain.ProjectGroupGithubRepository;
import team.po.feature.teamspace.dto.CompleteGithubAppInstallationRequest;
import team.po.feature.teamspace.dto.CreateGithubAppInstallationUrlResponse;
import team.po.feature.teamspace.dto.GetGithubInstallationStatusResponse;
import team.po.feature.teamspace.dto.GetGithubRepositoryListResponse;
import team.po.feature.teamspace.dto.GithubPullRequestInfo;
import team.po.feature.teamspace.dto.GithubPullRequestSummary;
import team.po.feature.teamspace.dto.GithubRepositorySettingContext;
import team.po.feature.teamspace.dto.SetGithubRepositoryListRequest;
import team.po.feature.teamspace.repository.GithubInstallationRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubInstallationRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubRepositoryRepository;
import team.po.feature.user.domain.GithubAccount;
import team.po.feature.user.domain.Users;
import team.po.feature.user.repository.GithubAccountRepository;
import team.po.feature.user.repository.UserRepository;
import team.po.feature.user.service.GithubTokenEncryptor;

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
	private GithubAccountRepository githubAccountRepository;

	@Mock
	private RedisService redisService;

	@Mock
	private GithubAppClient githubAppClient;

	@Mock
	private GithubTokenEncryptor githubTokenEncryptor;

	@Mock
	private TeamspacePersistenceTxService teamspacePersistenceTxService;

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
			githubAccountRepository,
			redisService,
			githubAppProperties,
			githubAppClient,
			githubTokenEncryptor,
			teamspacePersistenceTxService
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
	void getAvailableGithubRepositoryList_returnsGithubRepositories_whenGithubInstallationIsConnected() {
		Users requester = user();
		ProjectGroupGithubInstallation githubConnection = ProjectGroupGithubInstallation.builder()
			.projectGroup(projectGroup())
			.githubInstallation(githubInstallation())
			.connectedBy(requester)
			.build();
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_IdAndGroupRole(10L, 1L, GroupRole.HOST))
			.thenReturn(true);
		when(projectGroupGithubInstallationRepository.findByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(Optional.of(githubConnection));
		when(githubAppClient.getInstallationRepositories(12345L)).thenReturn(List.of(
			new GithubAppClient.GithubRepositoryInfo(100L, "student-team-org", "backend",
				"student-team-org/backend", "main", true),
			new GithubAppClient.GithubRepositoryInfo(200L, "student-team-org", "frontend",
				"student-team-org/frontend", "main", true)
		));

		var response = teamspaceService.getAvailableGithubRepositoryList(requester, 10L);

		assertThat(response.repositories()).hasSize(2);
		assertThat(response.repositories().get(0).githubRepositoryId()).isEqualTo(100L);
		assertThat(response.repositories().get(0).repoName()).isEqualTo("backend");
		assertThat(response.repositories().get(0).fullName()).isEqualTo("student-team-org/backend");
		verify(githubAppClient).getInstallationRepositories(12345L);
	}

	@Test
	void getAvailableGithubRepositoryList_throwsNotFound_whenGithubInstallationIsNotConnected() {
		Users requester = user();
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_IdAndGroupRole(10L, 1L, GroupRole.HOST))
			.thenReturn(true);
		when(projectGroupGithubInstallationRepository.findByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> teamspaceService.getAvailableGithubRepositoryList(requester, 10L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.GITHUB_APP_INSTALLATION_NOT_CONNECTED.getCode());

		verify(githubAppClient, never()).getInstallationRepositories(any());
	}

	@Test
	void getAvailableGithubRepositoryList_throwsForbidden_whenRequesterIsNotProjectGroupHost() {
		Users requester = user();
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_IdAndGroupRole(10L, 1L, GroupRole.HOST))
			.thenReturn(false);

		assertThatThrownBy(() -> teamspaceService.getAvailableGithubRepositoryList(requester, 10L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_PERMISSION_DENIED.getCode());

		verify(projectGroupGithubInstallationRepository, never()).findByProjectGroup_IdAndDeletedAtIsNull(10L);
		verify(githubAppClient, never()).getInstallationRepositories(any());
	}

	@Test
	void getGithubRepositoryList_returnsRegisteredRepositories_whenRequesterIsProjectGroupMember() {
		Users requester = user();
		ProjectGroup projectGroup = projectGroup();
		GithubInstallation githubInstallation = githubInstallation();
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(true);
		when(projectGroupGithubRepositoryRepository.findAllByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(List.of(
				ProjectGroupGithubRepository.builder()
					.projectGroup(projectGroup)
					.githubInstallation(githubInstallation)
					.githubRepositoryId(100L)
					.owner("student-team-org")
					.repoName("backend")
					.fullName("student-team-org/backend")
					.defaultBranch("main")
					.privateRepository(true)
					.build(),
				ProjectGroupGithubRepository.builder()
					.projectGroup(projectGroup)
					.githubInstallation(githubInstallation)
					.githubRepositoryId(200L)
					.owner("student-team-org")
					.repoName("frontend")
					.fullName("student-team-org/frontend")
					.defaultBranch("develop")
					.privateRepository(false)
					.build()
			));

		GetGithubRepositoryListResponse response = teamspaceService.getGithubRepositoryList(requester, 10L);

		assertThat(response.repositories()).hasSize(2);
		assertThat(response.repositories().get(0).githubRepositoryId()).isEqualTo(100L);
		assertThat(response.repositories().get(0).repoName()).isEqualTo("backend");
		assertThat(response.repositories().get(0).fullName()).isEqualTo("student-team-org/backend");
		assertThat(response.repositories().get(1).githubRepositoryId()).isEqualTo(200L);
	}

	@Test
	void getGithubRepositoryList_throwsForbidden_whenRequesterIsNotProjectGroupMember() {
		Users requester = user();
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_Id(10L, 1L)).thenReturn(false);

		assertThatThrownBy(() -> teamspaceService.getGithubRepositoryList(requester, 10L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_PERMISSION_DENIED.getCode());

		verify(projectGroupGithubRepositoryRepository, never()).findAllByProjectGroup_IdAndDeletedAtIsNull(10L);
	}

	@Test
	void setGithubRepositoryList_fetchesRepositoriesAndPersistsSetting_whenRequestIsValid() {
		Users requester = user();
		SetGithubRepositoryListRequest request = new SetGithubRepositoryListRequest(List.of(100L, 100L, 200L));
		List<GithubAppClient.GithubRepositoryInfo> repositories = List.of(
			new GithubAppClient.GithubRepositoryInfo(100L, "student-team-org", "backend",
				"student-team-org/backend", "main", true),
			new GithubAppClient.GithubRepositoryInfo(200L, "student-team-org", "frontend",
				"student-team-org/frontend", "develop", false)
		);
		when(teamspacePersistenceTxService.prepareGithubRepositorySetting(10L, 1L))
			.thenReturn(new GithubRepositorySettingContext(5L, 12345L));
		when(githubAppClient.getInstallationRepositories(12345L)).thenReturn(repositories);

		teamspaceService.setGithubRepositoryList(
			requester,
			10L,
			request
		);

		verify(githubAppClient).getInstallationRepositories(12345L);
		verify(teamspacePersistenceTxService).persistGithubRepositorySetting(
			10L,
			5L,
			request.githubRepositoryIds(),
			repositories
		);
	}

	@Test
	void setGithubRepositoryList_doesNotCallGithubApi_whenPrepareFails() {
		Users requester = user();
		SetGithubRepositoryListRequest request = new SetGithubRepositoryListRequest(List.of(999L));
		when(teamspacePersistenceTxService.prepareGithubRepositorySetting(10L, 1L))
			.thenThrow(new ApplicationException(ErrorCode.PROJECT_GROUP_PERMISSION_DENIED));

		assertThatThrownBy(() -> teamspaceService.setGithubRepositoryList(
			requester,
			10L,
			request
		))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_PERMISSION_DENIED.getCode());

		verify(githubAppClient, never()).getInstallationRepositories(any());
		verify(teamspacePersistenceTxService, never()).persistGithubRepositorySetting(any(), any(), any(), any());
	}

	@Test
	void setGithubRepositoryList_persistsEmptyRepositoryListWithoutGithubApiCall_whenRepositoryIdsAreEmpty() {
		Users requester = user();
		SetGithubRepositoryListRequest request = new SetGithubRepositoryListRequest(List.of());
		when(teamspacePersistenceTxService.prepareGithubRepositorySetting(10L, 1L))
			.thenReturn(new GithubRepositorySettingContext(5L, 12345L));

		teamspaceService.setGithubRepositoryList(requester, 10L, request);

		verify(githubAppClient, never()).getInstallationRepositories(any());
		verify(teamspacePersistenceTxService).persistGithubRepositorySetting(
			10L,
			5L,
			request.githubRepositoryIds(),
			List.of()
		);
	}

	@Test
	void setGithubRepositoryList_propagatesPersistException_whenRepositoryIsNotAccessible() {
		Users requester = user();
		SetGithubRepositoryListRequest request = new SetGithubRepositoryListRequest(List.of(999L));
		List<GithubAppClient.GithubRepositoryInfo> repositories = List.of(
			new GithubAppClient.GithubRepositoryInfo(100L, "student-team-org", "backend",
				"student-team-org/backend", "main", true)
		);
		when(teamspacePersistenceTxService.prepareGithubRepositorySetting(10L, 1L))
			.thenReturn(new GithubRepositorySettingContext(5L, 12345L));
		when(githubAppClient.getInstallationRepositories(12345L)).thenReturn(repositories);
		doThrow(new ApplicationException(ErrorCode.GITHUB_REPOSITORY_NOT_ACCESSIBLE))
			.when(teamspacePersistenceTxService)
			.persistGithubRepositorySetting(10L, 5L, request.githubRepositoryIds(), repositories);

		assertThatThrownBy(() -> teamspaceService.setGithubRepositoryList(requester, 10L, request))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.GITHUB_REPOSITORY_NOT_ACCESSIBLE.getCode());

		verify(githubAppClient).getInstallationRepositories(12345L);
	}

	@Test
	void syncGithubPullRequestContributions_fetchesMergedPullRequestDetailsAndPersists() {
		ProjectGroupGithubRepository repository = ProjectGroupGithubRepository.builder()
			.projectGroup(projectGroup())
			.githubInstallation(githubInstallation())
			.githubRepositoryId(100L)
			.owner("student-team-org")
			.repoName("backend")
			.fullName("student-team-org/backend")
			.defaultBranch("main")
			.privateRepository(true)
			.build();
		Instant mergedAt = Instant.parse("2026-05-02T10:00:00Z");
		GithubPullRequestSummary mergedPullRequest = new GithubPullRequestSummary(
			1001L,
			10L,
			"Add contribution sync",
			501L,
			"dev-a",
			"closed",
			mergedAt,
			"https://github.com/student-team-org/backend/pull/10"
		);
		GithubPullRequestSummary closedUnmergedPullRequest = new GithubPullRequestSummary(
			1002L,
			11L,
			"Close stale PR",
			502L,
			"dev-b",
			"closed",
			null,
			"https://github.com/student-team-org/backend/pull/11"
		);
		GithubPullRequestInfo pullRequestDetail = new GithubPullRequestInfo(
			1001L,
			10L,
			"Add contribution sync",
			501L,
			"dev-a",
			"closed",
			mergedAt,
			120,
			15,
			8,
			"https://github.com/student-team-org/backend/pull/10"
		);
		when(projectGroupGithubRepositoryRepository.findByProjectGroup_IdAndGithubRepositoryIdAndDeletedAtIsNull(
			10L,
			100L
		)).thenReturn(Optional.of(repository));
		when(githubAppClient.getClosedPullRequests(12345L, "student-team-org", "backend"))
			.thenReturn(List.of(mergedPullRequest, closedUnmergedPullRequest));
		when(githubAppClient.getPullRequest(12345L, "student-team-org", "backend", 10L))
			.thenReturn(pullRequestDetail);

		teamspaceService.syncGithubPullRequestContributions(10L, 100L);

		verify(githubAppClient).getClosedPullRequests(12345L, "student-team-org", "backend");
		verify(githubAppClient).getPullRequest(12345L, "student-team-org", "backend", 10L);
		verify(githubAppClient, never()).getPullRequest(12345L, "student-team-org", "backend", 11L);
		verify(teamspacePersistenceTxService).persistGithubPullRequestContributions(
			10L,
			100L,
			List.of(pullRequestDetail)
		);
	}

	@Test
	void syncGithubPullRequestContributions_throwsBadRequest_whenRepositoryIsNotRegistered() {
		when(projectGroupGithubRepositoryRepository.findByProjectGroup_IdAndGithubRepositoryIdAndDeletedAtIsNull(
			10L,
			999L
		)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> teamspaceService.syncGithubPullRequestContributions(10L, 999L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.GITHUB_REPOSITORY_NOT_ACCESSIBLE.getCode());

		verify(githubAppClient, never()).getClosedPullRequests(any(), anyString(), anyString());
		verify(teamspacePersistenceTxService, never()).persistGithubPullRequestContributions(any(), any(), any());
	}

	@Test
	void syncGithubPullRequestContributions_withUser_validatesHostAndSyncsRepository() {
		Users requester = user();
		ProjectGroupGithubRepository repository = ProjectGroupGithubRepository.builder()
			.projectGroup(projectGroup())
			.githubInstallation(githubInstallation())
			.githubRepositoryId(100L)
			.owner("student-team-org")
			.repoName("backend")
			.fullName("student-team-org/backend")
			.defaultBranch("main")
			.privateRepository(true)
			.build();
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_IdAndGroupRole(10L, 1L, GroupRole.HOST))
			.thenReturn(true);
		when(projectGroupGithubRepositoryRepository.findByProjectGroup_IdAndGithubRepositoryIdAndDeletedAtIsNull(
			10L,
			100L
		)).thenReturn(Optional.of(repository));
		when(githubAppClient.getClosedPullRequests(12345L, "student-team-org", "backend"))
			.thenReturn(List.of());

		teamspaceService.syncGithubPullRequestContributions(requester, 10L, 100L);

		verify(projectGroupMemberRepository).existsByProjectGroup_IdAndUser_IdAndGroupRole(
			10L,
			1L,
			GroupRole.HOST
		);
		verify(teamspacePersistenceTxService).persistGithubPullRequestContributions(10L, 100L, List.of());
	}

	@Test
	void syncGithubPullRequestContributions_withUser_throwsForbidden_whenRequesterIsNotHost() {
		Users requester = user();
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_IdAndGroupRole(10L, 1L, GroupRole.HOST))
			.thenReturn(false);

		assertThatThrownBy(() -> teamspaceService.syncGithubPullRequestContributions(requester, 10L, 100L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_PERMISSION_DENIED.getCode());

		verify(projectGroupGithubRepositoryRepository, never())
			.findByProjectGroup_IdAndGithubRepositoryIdAndDeletedAtIsNull(any(), any());
		verify(githubAppClient, never()).getClosedPullRequests(any(), anyString(), anyString());
		verify(teamspacePersistenceTxService, never()).persistGithubPullRequestContributions(any(), any(), any());
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
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(githubAccount()));
		when(githubTokenEncryptor.decrypt("encrypted-token")).thenReturn("github-access-token");
		when(githubInstallationRepository.findByInstallationIdAndDeletedAtIsNull(12345L))
			.thenReturn(Optional.empty());
		when(githubInstallationRepository.save(any(GithubInstallation.class))).thenReturn(savedGithubInstallation);
		when(projectGroupRepository.findById(10L)).thenReturn(Optional.of(projectGroup()));
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user()));

		teamspaceService.completeGithubAppInstallation(request, 10L, 1L);

		verify(redisService).getAndDeleteStringValue("github-app-install-state:test-state");
		verify(githubAppClient).getInstallation(12345L);
		verify(githubAppClient).validateOrganizationAdmin("github-access-token", "student-team-org");
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
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(githubAccount()));
		when(githubTokenEncryptor.decrypt("encrypted-token")).thenReturn("github-access-token");
		when(githubInstallationRepository.findByInstallationIdAndDeletedAtIsNull(12345L))
			.thenReturn(Optional.of(githubInstallation));
		when(projectGroupRepository.findById(10L)).thenReturn(Optional.of(projectGroup()));
		when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(user()));

		teamspaceService.completeGithubAppInstallation(request, 10L, 1L);

		assertThat(githubInstallation.getAccountId()).isEqualTo(98765L);
		assertThat(githubInstallation.getAccountLogin()).isEqualTo("student-team-org");
		verify(githubAppClient).validateOrganizationAdmin("github-access-token", "student-team-org");
		verify(githubInstallationRepository, never()).save(any(GithubInstallation.class));
		verify(projectGroupGithubInstallationRepository).save(any(ProjectGroupGithubInstallation.class));
	}

	@Test
	void completeGithubAppInstallation_throwsNotFound_whenRequesterHasNoGithubAccount() {
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
			.thenReturn(false);
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> teamspaceService.completeGithubAppInstallation(request, 10L, 1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.GITHUB_ACCOUNT_NOT_LINKED.getCode());

		verify(githubAppClient, never()).validateOrganizationAdmin(anyString(), anyString());
		verify(githubInstallationRepository, never()).save(any(GithubInstallation.class));
		verify(projectGroupGithubInstallationRepository, never()).save(any(ProjectGroupGithubInstallation.class));
	}

	@Test
	void completeGithubAppInstallation_throwsForbidden_whenRequesterIsNotOrganizationAdmin() {
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
			.thenReturn(false);
		when(githubAccountRepository.findByUserIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(githubAccount()));
		when(githubTokenEncryptor.decrypt("encrypted-token")).thenReturn("github-access-token");
		doThrow(new ApplicationException(ErrorCode.GITHUB_ORGANIZATION_PERMISSION_DENIED))
			.when(githubAppClient).validateOrganizationAdmin("github-access-token", "student-team-org");

		assertThatThrownBy(() -> teamspaceService.completeGithubAppInstallation(request, 10L, 1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.GITHUB_ORGANIZATION_PERMISSION_DENIED.getCode());

		verify(githubInstallationRepository, never()).save(any(GithubInstallation.class));
		verify(projectGroupGithubInstallationRepository, never()).save(any(ProjectGroupGithubInstallation.class));
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
		GithubInstallation githubInstallation = GithubInstallation.builder()
			.installationId(12345L)
			.accountId(98765L)
			.accountLogin("student-team-org")
			.accountType(GithubInstallation.ORGANIZATION_ACCOUNT_TYPE)
			.build();
		ReflectionTestUtils.setField(githubInstallation, "id", 5L);
		return githubInstallation;
	}

	private GithubAccount githubAccount() {
		return GithubAccount.builder()
			.user(user())
			.githubUserId(123L)
			.githubUsername("octocat")
			.accessTokenCiphertext("encrypted-token")
			.tokenType("Bearer")
			.githubScopes("read:user,user:email,read:org")
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
