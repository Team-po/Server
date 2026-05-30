package team.po.feature.teamspace.service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import team.po.exception.ApplicationException;
import team.po.exception.ErrorCode;
import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.ProjectGroup;
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

@Service
@RequiredArgsConstructor
public class TeamspacePersistenceTxService {

	private final ProjectGroupMemberRepository projectGroupMemberRepository;
	private final ProjectGroupRepository projectGroupRepository;
	private final ProjectGroupGithubInstallationRepository projectGroupGithubInstallationRepository;
	private final ProjectGroupGithubRepositoryRepository projectGroupGithubRepositoryRepository;
	private final GithubInstallationRepository githubInstallationRepository;
	private final GithubPullRequestContributionRepository githubPullRequestContributionRepository;

	@Transactional(readOnly = true)
	public GithubRepositorySettingContext prepareGithubRepositorySetting(Long projectGroupId, Long requesterUserId) {
		validateProjectGroupHost(projectGroupId, requesterUserId);
		ProjectGroupGithubInstallation connection = projectGroupGithubInstallationRepository
			.findByProjectGroup_IdAndDeletedAtIsNull(projectGroupId)
			.orElseThrow(() -> new ApplicationException(
				ErrorCode.GITHUB_APP_INSTALLATION_NOT_CONNECTED,
				"Github Organization이 연결되지 않은 팀 스페이스입니다."
			));

		GithubInstallation installation = connection.getGithubInstallation();
		return new GithubRepositorySettingContext(
			installation.getId(),
			installation.getInstallationId()
		);
	}

	@Transactional
	public void persistGithubRepositorySetting(
		Long projectGroupId,
		Long githubInstallationId,
		List<Long> githubRepositoryIds,
		List<GithubAppClient.GithubRepositoryInfo> accessibleRepositories
	) {
		Map<Long, GithubAppClient.GithubRepositoryInfo> accessibleRepositoryMap = accessibleRepositories.stream()
			.collect(Collectors.toMap(
				GithubAppClient.GithubRepositoryInfo::githubRepositoryId,
				Function.identity(),
				(first, second) -> first
			));
		Set<Long> selectedGithubRepositoryIds = new LinkedHashSet<>(githubRepositoryIds);

		boolean hasInaccessibleRepository = selectedGithubRepositoryIds.stream()
			.anyMatch(repositoryId -> !accessibleRepositoryMap.containsKey(repositoryId));

		if (hasInaccessibleRepository) {
			throw new ApplicationException(
				ErrorCode.GITHUB_REPOSITORY_NOT_ACCESSIBLE,
				"선택한 Github Repository에 접근할 수 없습니다."
			);
		}

		ProjectGroup projectGroup = projectGroupRepository.findByIdForUpdate(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		Instant deletedAt = Instant.now();
		List<ProjectGroupGithubRepository> activeRepositories = projectGroupGithubRepositoryRepository
			.findAllByProjectGroup_IdAndDeletedAtIsNull(projectGroupId);
		Map<Long, ProjectGroupGithubRepository> activeRepositoryMap = activeRepositories.stream()
			.collect(Collectors.toMap(
				ProjectGroupGithubRepository::getGithubRepositoryId,
				Function.identity(),
				(first, second) -> first
			));

		activeRepositories.forEach(repository -> {
			Long githubRepositoryId = repository.getGithubRepositoryId();
			if (!selectedGithubRepositoryIds.contains(githubRepositoryId)) {
				repository.softDelete(deletedAt);
				return;
			}

			GithubAppClient.GithubRepositoryInfo latestRepositoryInfo = accessibleRepositoryMap.get(githubRepositoryId);
			repository.updateRepositoryInfo(
				latestRepositoryInfo.owner(),
				latestRepositoryInfo.repoName(),
				latestRepositoryInfo.fullName(),
				latestRepositoryInfo.defaultBranch(),
				latestRepositoryInfo.privateRepository()
			);
		});

		Set<Long> newGithubRepositoryIds = selectedGithubRepositoryIds.stream()
			.filter(repositoryId -> !activeRepositoryMap.containsKey(repositoryId))
			.collect(Collectors.toCollection(LinkedHashSet::new));

		if (newGithubRepositoryIds.isEmpty()) {
			return;
		}

		GithubInstallation githubInstallation = githubInstallationRepository.findByIdAndDeletedAtIsNull(githubInstallationId)
			.orElseThrow(() -> new ApplicationException(
				ErrorCode.GITHUB_APP_INSTALLATION_NOT_CONNECTED,
				"Github Organization이 연결되지 않은 팀 스페이스입니다."
			));

		List<ProjectGroupGithubRepository> selectedRepositories = newGithubRepositoryIds.stream()
			.map(accessibleRepositoryMap::get)
			.map(repository -> ProjectGroupGithubRepository.builder()
				.projectGroup(projectGroup)
				.githubInstallation(githubInstallation)
				.githubRepositoryId(repository.githubRepositoryId())
				.owner(repository.owner())
				.repoName(repository.repoName())
				.fullName(repository.fullName())
				.defaultBranch(repository.defaultBranch())
				.privateRepository(repository.privateRepository())
				.build())
			.toList();

		projectGroupGithubRepositoryRepository.saveAll(selectedRepositories);
	}

	@Transactional
	public void persistGithubPullRequestContributions(
		Long projectGroupId,
		Long githubRepositoryId,
		List<GithubPullRequestInfo> pullRequests
	) {
		ProjectGroup projectGroup = projectGroupRepository.findByIdForUpdate(projectGroupId)
			.orElseThrow(() -> new ApplicationException(ErrorCode.PROJECT_GROUP_NOT_FOUND));

		List<GithubPullRequestContribution> newContributions = pullRequests.stream()
			.map(pullRequest -> upsertGithubPullRequestContribution(
				projectGroupId,
				projectGroup,
				githubRepositoryId,
				pullRequest
			))
			.filter(contribution -> contribution.getId() == null)
			.toList();

		if (newContributions.isEmpty()) {
			return;
		}

		githubPullRequestContributionRepository.saveAll(newContributions);
	}

	private GithubPullRequestContribution upsertGithubPullRequestContribution(
		Long projectGroupId,
		ProjectGroup projectGroup,
		Long githubRepositoryId,
		GithubPullRequestInfo pullRequest
	) {
		return githubPullRequestContributionRepository
			.findByProjectGroup_IdAndGithubRepositoryIdAndGithubPrId(
				projectGroupId,
				githubRepositoryId,
				pullRequest.githubPullRequestId()
			)
			.map(existingContribution -> {
				existingContribution.updatePullRequestContribution(
					pullRequest.title(),
					pullRequest.authorGithubUserId(),
					pullRequest.authorGithubUsername(),
					pullRequest.state(),
					pullRequest.mergedAt() != null,
					pullRequest.mergedAt(),
					toNonNegativeInt(pullRequest.additions()),
					toNonNegativeInt(pullRequest.deletions()),
					toNonNegativeInt(pullRequest.changedFiles()),
					toNonNegativeInt(pullRequest.linkedIssueCount()),
					pullRequest.htmlUrl(),
					Instant.now()
				);
				return existingContribution;
			})
			.orElseGet(() -> GithubPullRequestContribution.builder()
				.projectGroup(projectGroup)
				.githubRepositoryId(githubRepositoryId)
				.githubPrId(pullRequest.githubPullRequestId())
				.prNumber(pullRequest.pullNumber())
				.title(pullRequest.title())
				.authorGithubUserId(pullRequest.authorGithubUserId())
				.authorGithubUsername(pullRequest.authorGithubUsername())
				.state(pullRequest.state())
				.merged(pullRequest.mergedAt() != null)
				.mergedAt(pullRequest.mergedAt())
				.additions(toNonNegativeInt(pullRequest.additions()))
				.deletions(toNonNegativeInt(pullRequest.deletions()))
				.changedFiles(toNonNegativeInt(pullRequest.changedFiles()))
				.linkedIssueCount(toNonNegativeInt(pullRequest.linkedIssueCount()))
				.htmlUrl(pullRequest.htmlUrl())
				.syncedAt(Instant.now())
				.build());
	}

	private int toNonNegativeInt(Integer value) {
		if (value == null) {
			return 0;
		}
		return Math.max(value, 0);
	}

	private void validateProjectGroupHost(Long projectGroupId, Long requesterUserId) {
		boolean exists = projectGroupMemberRepository.existsByProjectGroup_IdAndUser_IdAndGroupRole(
			projectGroupId,
			requesterUserId,
			GroupRole.HOST
		);

		if (!exists) {
			throw new ApplicationException(
				ErrorCode.PROJECT_GROUP_PERMISSION_DENIED,
				"팀 스페이스 호스트만 Github Organization 연결을 진행할 수 있습니다."
			);
		}
	}

}
