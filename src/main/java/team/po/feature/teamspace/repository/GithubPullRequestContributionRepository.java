package team.po.feature.teamspace.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.teamspace.domain.GithubPullRequestContribution;

public interface GithubPullRequestContributionRepository extends JpaRepository<GithubPullRequestContribution, Long> {
	Optional<GithubPullRequestContribution>
	findByProjectGroup_IdAndGithubRepositoryIdAndGithubPrId(
		Long projectGroupId,
		Long githubRepositoryId,
		Long githubPrId
	);
}
