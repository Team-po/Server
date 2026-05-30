package team.po.feature.teamspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.ProjectGroup;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;
import team.po.feature.projectgroup.repository.ProjectGroupMemberRepository;
import team.po.feature.projectgroup.repository.ProjectGroupRepository;
import team.po.feature.teamspace.domain.GithubInstallation;
import team.po.feature.teamspace.domain.GithubPullRequestContribution;
import team.po.feature.teamspace.domain.ProjectGroupGithubInstallation;
import team.po.feature.teamspace.domain.ProjectGroupGithubRepository;
import team.po.feature.teamspace.dto.GithubPullRequestInfo;
import team.po.feature.teamspace.dto.GithubRepositorySettingContext;
import team.po.feature.teamspace.repository.GithubInstallationRepository;
import team.po.feature.teamspace.repository.GithubPullRequestContributionRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubInstallationRepository;
import team.po.feature.teamspace.repository.ProjectGroupGithubRepositoryRepository;
import team.po.feature.user.domain.Users;

@ExtendWith(MockitoExtension.class)
class TeamspacePersistenceTxServiceTest {

	@Mock
	private ProjectGroupMemberRepository projectGroupMemberRepository;

	@Mock
	private ProjectGroupRepository projectGroupRepository;

	@Mock
	private ProjectGroupGithubInstallationRepository projectGroupGithubInstallationRepository;

	@Mock
	private ProjectGroupGithubRepositoryRepository projectGroupGithubRepositoryRepository;

	@Mock
	private GithubInstallationRepository githubInstallationRepository;

	@Mock
	private GithubPullRequestContributionRepository githubPullRequestContributionRepository;

	private TeamspacePersistenceTxService service;

	@BeforeEach
	void setUp() {
		service = new TeamspacePersistenceTxService(
			projectGroupMemberRepository,
			projectGroupRepository,
			projectGroupGithubInstallationRepository,
			projectGroupGithubRepositoryRepository,
			githubInstallationRepository,
			githubPullRequestContributionRepository
		);
	}

	@Test
	void prepareGithubRepositorySetting_returnsInstallationIds_whenRequesterIsHostAndConnected() {
		ProjectGroupGithubInstallation connection = ProjectGroupGithubInstallation.builder()
			.projectGroup(projectGroup())
			.githubInstallation(githubInstallation())
			.connectedBy(user())
			.build();
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_IdAndGroupRole(10L, 1L, GroupRole.HOST))
			.thenReturn(true);
		when(projectGroupGithubInstallationRepository.findByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(Optional.of(connection));

		GithubRepositorySettingContext context = service.prepareGithubRepositorySetting(10L, 1L);

		assertThat(context.githubInstallationId()).isEqualTo(5L);
		assertThat(context.installationId()).isEqualTo(12345L);
	}

	@Test
	void prepareGithubRepositorySetting_throwsForbidden_whenRequesterIsNotHost() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_IdAndGroupRole(10L, 1L, GroupRole.HOST))
			.thenReturn(false);

		assertThatThrownBy(() -> service.prepareGithubRepositorySetting(10L, 1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_PERMISSION_DENIED.getCode());

		verify(projectGroupGithubInstallationRepository, never()).findByProjectGroup_IdAndDeletedAtIsNull(10L);
	}

	@Test
	void prepareGithubRepositorySetting_throwsNotFound_whenGithubInstallationIsNotConnected() {
		when(projectGroupMemberRepository.existsByProjectGroup_IdAndUser_IdAndGroupRole(10L, 1L, GroupRole.HOST))
			.thenReturn(true);
		when(projectGroupGithubInstallationRepository.findByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.prepareGithubRepositorySetting(10L, 1L))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.GITHUB_APP_INSTALLATION_NOT_CONNECTED.getCode());
	}

	@Test
	void persistGithubRepositorySetting_locksProjectGroupAndReplacesRepositories_whenRequestIsValid() {
		ProjectGroup projectGroup = projectGroup();
		GithubInstallation githubInstallation = githubInstallation();
		ProjectGroupGithubRepository retainedRepository = ProjectGroupGithubRepository.builder()
			.projectGroup(projectGroup)
			.githubInstallation(githubInstallation)
			.githubRepositoryId(100L)
			.owner("student-team-org")
			.repoName("backend-old")
			.fullName("student-team-org/backend-old")
			.defaultBranch("develop")
			.privateRepository(false)
			.build();
		ProjectGroupGithubRepository removedRepository = ProjectGroupGithubRepository.builder()
			.projectGroup(projectGroup)
			.githubInstallation(githubInstallation)
			.githubRepositoryId(999L)
			.owner("student-team-org")
			.repoName("old")
			.fullName("student-team-org/old")
			.defaultBranch("main")
			.privateRepository(true)
			.build();
		List<GithubAppClient.GithubRepositoryInfo> repositories = List.of(
			new GithubAppClient.GithubRepositoryInfo(100L, "student-team-org", "backend",
				"student-team-org/backend", "main", true),
			new GithubAppClient.GithubRepositoryInfo(200L, "student-team-org", "frontend",
				"student-team-org/frontend", "develop", false)
		);
		when(projectGroupRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(projectGroup));
		when(projectGroupGithubRepositoryRepository.findAllByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(List.of(retainedRepository, removedRepository));
		when(githubInstallationRepository.findByIdAndDeletedAtIsNull(5L)).thenReturn(Optional.of(githubInstallation));

		service.persistGithubRepositorySetting(
			10L,
			5L,
			List.of(100L, 100L, 200L),
			repositories
		);

		verify(projectGroupRepository).findByIdForUpdate(10L);
		assertThat(retainedRepository.getDeletedAt()).isNull();
		assertThat(retainedRepository.getRepoName()).isEqualTo("backend");
		assertThat(retainedRepository.getFullName()).isEqualTo("student-team-org/backend");
		assertThat(retainedRepository.getDefaultBranch()).isEqualTo("main");
		assertThat(retainedRepository.isPrivateRepository()).isTrue();
		assertThat(removedRepository.getDeletedAt()).isNotNull();

		ArgumentCaptor<List<ProjectGroupGithubRepository>> repositoryCaptor = ArgumentCaptor.forClass(List.class);
		verify(projectGroupGithubRepositoryRepository).saveAll(repositoryCaptor.capture());
		assertThat(repositoryCaptor.getValue()).hasSize(1);
		ProjectGroupGithubRepository newRepository = repositoryCaptor.getValue().get(0);
		assertThat(newRepository.getGithubRepositoryId()).isEqualTo(200L);
		assertThat(newRepository.getOwner()).isEqualTo("student-team-org");
		assertThat(newRepository.getRepoName()).isEqualTo("frontend");
		assertThat(newRepository.getFullName()).isEqualTo("student-team-org/frontend");
		assertThat(newRepository.getDefaultBranch()).isEqualTo("develop");
		assertThat(newRepository.isPrivateRepository()).isFalse();
	}

	@Test
	void persistGithubRepositorySetting_throwsBadRequestBeforeLock_whenRepositoryIsNotAccessible() {
		List<GithubAppClient.GithubRepositoryInfo> repositories = List.of(
			new GithubAppClient.GithubRepositoryInfo(100L, "student-team-org", "backend",
				"student-team-org/backend", "main", true)
		);

		assertThatThrownBy(() -> service.persistGithubRepositorySetting(
			10L,
			5L,
			List.of(999L),
			repositories
		))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.GITHUB_REPOSITORY_NOT_ACCESSIBLE.getCode());

		verify(projectGroupRepository, never()).findByIdForUpdate(any());
		verify(projectGroupGithubRepositoryRepository, never()).saveAll(any());
	}

	@Test
	void persistGithubRepositorySetting_keepsActiveRepository_whenSameRepositoryIsRequested() {
		ProjectGroup projectGroup = projectGroup();
		GithubInstallation githubInstallation = githubInstallation();
		ProjectGroupGithubRepository activeRepository = ProjectGroupGithubRepository.builder()
			.projectGroup(projectGroup)
			.githubInstallation(githubInstallation)
			.githubRepositoryId(100L)
			.owner("student-team-org")
			.repoName("backend-old")
			.fullName("student-team-org/backend-old")
			.defaultBranch("develop")
			.privateRepository(false)
			.build();
		List<GithubAppClient.GithubRepositoryInfo> repositories = List.of(
			new GithubAppClient.GithubRepositoryInfo(100L, "student-team-org", "backend",
				"student-team-org/backend", "main", true)
		);
		when(projectGroupRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(projectGroup));
		when(projectGroupGithubRepositoryRepository.findAllByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(List.of(activeRepository));

		service.persistGithubRepositorySetting(
			10L,
			5L,
			List.of(100L),
			repositories
		);

		assertThat(activeRepository.getDeletedAt()).isNull();
		assertThat(activeRepository.getRepoName()).isEqualTo("backend");
		assertThat(activeRepository.getFullName()).isEqualTo("student-team-org/backend");
		assertThat(activeRepository.getDefaultBranch()).isEqualTo("main");
		assertThat(activeRepository.isPrivateRepository()).isTrue();
		verify(githubInstallationRepository, never()).findByIdAndDeletedAtIsNull(any());
		verify(projectGroupGithubRepositoryRepository, never()).saveAll(any());
	}

	@Test
	void persistGithubRepositorySetting_softDeletesAllActiveRepositories_whenRepositoryIdsAreEmpty() {
		ProjectGroup projectGroup = projectGroup();
		GithubInstallation githubInstallation = githubInstallation();
		ProjectGroupGithubRepository activeRepository = ProjectGroupGithubRepository.builder()
			.projectGroup(projectGroup)
			.githubInstallation(githubInstallation)
			.githubRepositoryId(100L)
			.owner("student-team-org")
			.repoName("backend")
			.fullName("student-team-org/backend")
			.defaultBranch("main")
			.privateRepository(true)
			.build();
		when(projectGroupRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(projectGroup));
		when(projectGroupGithubRepositoryRepository.findAllByProjectGroup_IdAndDeletedAtIsNull(10L))
			.thenReturn(List.of(activeRepository));

		service.persistGithubRepositorySetting(
			10L,
			5L,
			List.of(),
			List.of()
		);

		assertThat(activeRepository.getDeletedAt()).isNotNull();
		verify(githubInstallationRepository, never()).findByIdAndDeletedAtIsNull(any());
		verify(projectGroupGithubRepositoryRepository, never()).saveAll(any());
	}

	@Test
	void persistGithubPullRequestContributions_updatesExistingAndSavesNewContributions() {
		ProjectGroup projectGroup = projectGroup();
		GithubPullRequestContribution existingContribution = GithubPullRequestContribution.builder()
			.projectGroup(projectGroup)
			.githubRepositoryId(100L)
			.githubPrId(1001L)
			.prNumber(10L)
			.title("Old title")
			.authorGithubUserId(501L)
			.authorGithubUsername("old-dev")
			.state("open")
			.merged(false)
			.mergedAt(null)
			.additions(1)
			.deletions(2)
			.changedFiles(1)
			.linkedIssueCount(0)
			.htmlUrl("https://github.com/student-team-org/backend/pull/10")
			.syncedAt(Instant.parse("2026-05-01T00:00:00Z"))
			.build();
		ReflectionTestUtils.setField(existingContribution, "id", 1L);
		Instant mergedAt = Instant.parse("2026-05-02T10:00:00Z");
		GithubPullRequestInfo existingPullRequest = new GithubPullRequestInfo(
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
			2,
			"https://github.com/student-team-org/backend/pull/10"
		);
		GithubPullRequestInfo newPullRequest = new GithubPullRequestInfo(
			1002L,
			11L,
			"Add contribution query",
			502L,
			"dev-b",
			"closed",
			Instant.parse("2026-05-03T10:00:00Z"),
			80,
			5,
			4,
			1,
			"https://github.com/student-team-org/backend/pull/11"
		);
		when(projectGroupRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(projectGroup));
		when(githubPullRequestContributionRepository.findByProjectGroup_IdAndGithubRepositoryIdAndGithubPrId(
			10L,
			100L,
			1001L
		)).thenReturn(Optional.of(existingContribution));
		when(githubPullRequestContributionRepository.findByProjectGroup_IdAndGithubRepositoryIdAndGithubPrId(
			10L,
			100L,
			1002L
		)).thenReturn(Optional.empty());

		service.persistGithubPullRequestContributions(
			10L,
			100L,
			List.of(existingPullRequest, newPullRequest)
		);

		verify(projectGroupRepository).findByIdForUpdate(10L);
		assertThat(existingContribution.getTitle()).isEqualTo("Add contribution sync");
		assertThat(existingContribution.getAuthorGithubUsername()).isEqualTo("dev-a");
		assertThat(existingContribution.getState()).isEqualTo("closed");
		assertThat(existingContribution.isMerged()).isTrue();
		assertThat(existingContribution.getMergedAt()).isEqualTo(mergedAt);
		assertThat(existingContribution.getAdditions()).isEqualTo(120);
		assertThat(existingContribution.getDeletions()).isEqualTo(15);
		assertThat(existingContribution.getChangedFiles()).isEqualTo(8);
		assertThat(existingContribution.getLinkedIssueCount()).isEqualTo(2);
		assertThat(existingContribution.getSyncedAt()).isNotNull();

		ArgumentCaptor<List<GithubPullRequestContribution>> contributionCaptor = ArgumentCaptor.forClass(List.class);
		verify(githubPullRequestContributionRepository).saveAll(contributionCaptor.capture());
		assertThat(contributionCaptor.getValue()).hasSize(1);
		GithubPullRequestContribution newContribution = contributionCaptor.getValue().get(0);
		assertThat(newContribution.getGithubRepositoryId()).isEqualTo(100L);
		assertThat(newContribution.getGithubPrId()).isEqualTo(1002L);
		assertThat(newContribution.getPrNumber()).isEqualTo(11L);
		assertThat(newContribution.getTitle()).isEqualTo("Add contribution query");
		assertThat(newContribution.getAuthorGithubUserId()).isEqualTo(502L);
		assertThat(newContribution.getAuthorGithubUsername()).isEqualTo("dev-b");
		assertThat(newContribution.isMerged()).isTrue();
		assertThat(newContribution.getAdditions()).isEqualTo(80);
		assertThat(newContribution.getDeletions()).isEqualTo(5);
		assertThat(newContribution.getChangedFiles()).isEqualTo(4);
		assertThat(newContribution.getLinkedIssueCount()).isEqualTo(1);
		assertThat(newContribution.getSyncedAt()).isNotNull();
	}

	@Test
	void persistGithubPullRequestContributions_throwsNotFound_whenProjectGroupDoesNotExist() {
		when(projectGroupRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.persistGithubPullRequestContributions(
			10L,
			100L,
			List.of(new GithubPullRequestInfo(
				1001L,
				10L,
				"Add contribution sync",
				501L,
				"dev-a",
				"closed",
				Instant.parse("2026-05-02T10:00:00Z"),
				120,
				15,
				8,
				0,
				"https://github.com/student-team-org/backend/pull/10"
			))
		))
			.isInstanceOf(ApplicationException.class)
			.extracting("code")
			.isEqualTo(ErrorCode.PROJECT_GROUP_NOT_FOUND.getCode());

		verify(githubPullRequestContributionRepository, never())
			.findByProjectGroup_IdAndGithubRepositoryIdAndGithubPrId(any(), any(), any());
		verify(githubPullRequestContributionRepository, never()).saveAll(any());
	}

	private ProjectGroup projectGroup() {
		ProjectGroup projectGroup = ProjectGroup.builder()
			.projectName("TeamPo")
			.projectTitle("TeamPo")
			.status(ProjectGroupStatus.ACTIVE)
			.build();
		ReflectionTestUtils.setField(projectGroup, "id", 10L);
		return projectGroup;
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
