package team.po.feature.teamspace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import team.po.feature.teamspace.domain.GithubPullRequestContribution;

public interface GithubPullRequestContributionRepository extends JpaRepository<GithubPullRequestContribution, Long> {
	Optional<GithubPullRequestContribution>
	findByProjectGroup_IdAndGithubRepositoryIdAndGithubPrId(
		Long projectGroupId,
		Long githubRepositoryId,
		Long githubPrId
	);

	@Query("""
		SELECT
			contribution.authorGithubUserId AS githubUserId,
			MAX(contribution.authorGithubUsername) AS githubUsername,
			COUNT(contribution.id) AS mergedPrCount,
			COALESCE(SUM(contribution.linkedIssueCount), 0) AS linkedIssueCount,
			COALESCE(SUM(contribution.additions), 0) AS additions,
			COALESCE(SUM(contribution.deletions), 0) AS deletions,
			COALESCE(SUM(contribution.changedFiles), 0) AS changedFiles
		FROM GithubPullRequestContribution contribution
		WHERE contribution.projectGroup.id = :projectGroupId
			AND contribution.githubRepositoryId = :githubRepositoryId
			AND contribution.merged = true
		GROUP BY contribution.authorGithubUserId
		ORDER BY COUNT(contribution.id) DESC, COALESCE(SUM(contribution.linkedIssueCount), 0) DESC
		""")
	List<GithubRepositoryContributionSummary> findContributionSummaries(
		@Param("projectGroupId") Long projectGroupId,
		@Param("githubRepositoryId") Long githubRepositoryId
	);

	interface GithubRepositoryContributionSummary {
		Long getGithubUserId();

		String getGithubUsername();

		long getMergedPrCount();

		long getLinkedIssueCount();

		long getAdditions();

		long getDeletions();

		long getChangedFiles();
	}
}
