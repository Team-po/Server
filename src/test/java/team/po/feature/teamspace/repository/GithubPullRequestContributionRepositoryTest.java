package team.po.feature.teamspace.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import team.po.feature.teamspace.repository.GithubPullRequestContributionRepository.GithubRepositoryContributionSummary;

@DataJpaTest
@ActiveProfiles("h2")
class GithubPullRequestContributionRepositoryTest {

	@Autowired
	private GithubPullRequestContributionRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void findContributionSummaries_returnsOnlyActiveGithubAccountsInProjectGroupMembers() {
		insertUser(1L, "member-a@example.com", "member-a");
		insertUser(2L, "member-b@example.com", "member-b");
		insertUser(3L, "outsider@example.com", "outsider");
		insertUser(4L, "deleted-github@example.com", "deleted-github");
		insertProjectGroup(10L);
		insertProjectGroupMember(1L, 10L);
		insertProjectGroupMember(2L, 10L);
		insertProjectGroupMember(4L, 10L);
		insertGithubAccount(1L, 1L, 501L, "dev-a", null);
		insertGithubAccount(2L, 2L, 502L, "dev-b", null);
		insertGithubAccount(3L, 3L, 503L, "outsider", null);
		insertGithubAccount(4L, 4L, 504L, "deleted-dev", Instant.parse("2026-05-03T00:00:00Z"));
		insertContribution(1L, 10L, 100L, 1001L, 501L, "dev-a", true, 2, 100, 10, 4);
		insertContribution(2L, 10L, 100L, 1002L, 501L, "dev-a-old", true, 1, 20, 5, 2);
		insertContribution(3L, 10L, 100L, 1003L, 502L, "dev-b", true, 0, 70, 7, 3);
		insertContribution(4L, 10L, 100L, 1004L, 503L, "outsider", true, 10, 999, 999, 99);
		insertContribution(5L, 10L, 100L, 1005L, 504L, "deleted-dev", true, 10, 999, 999, 99);
		insertContribution(6L, 10L, 100L, 1006L, 501L, "dev-a", false, 10, 999, 999, 99);
		insertContribution(7L, 10L, 200L, 2001L, 501L, "dev-a", true, 10, 999, 999, 99);
		entityManager.flush();
		entityManager.clear();

		List<GithubRepositoryContributionSummary> summaries = repository.findContributionSummaries(10L, 100L);

		assertThat(summaries).hasSize(2);
		GithubRepositoryContributionSummary first = summaries.get(0);
		assertThat(first.getUserId()).isEqualTo(1L);
		assertThat(first.getGithubUserId()).isEqualTo(501L);
		assertThat(first.getGithubUsername()).isEqualTo("dev-a");
		assertThat(first.getMergedPrCount()).isEqualTo(2L);
		assertThat(first.getLinkedIssueCount()).isEqualTo(3L);
		assertThat(first.getAdditions()).isEqualTo(120L);
		assertThat(first.getDeletions()).isEqualTo(15L);
		assertThat(first.getChangedFiles()).isEqualTo(6L);

		GithubRepositoryContributionSummary second = summaries.get(1);
		assertThat(second.getUserId()).isEqualTo(2L);
		assertThat(second.getGithubUserId()).isEqualTo(502L);
		assertThat(second.getGithubUsername()).isEqualTo("dev-b");
		assertThat(second.getMergedPrCount()).isEqualTo(1L);
		assertThat(second.getLinkedIssueCount()).isZero();
		assertThat(second.getAdditions()).isEqualTo(70L);
		assertThat(second.getDeletions()).isEqualTo(7L);
		assertThat(second.getChangedFiles()).isEqualTo(3L);
	}

	private void insertUser(Long id, String email, String nickname) {
		entityManager.createNativeQuery("""
			INSERT INTO users (
				id, email, nickname, temperature, level, is_github_login, created_at
			) VALUES (
				:id, :email, :nickname, 50, 1, false, CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.setParameter("email", email)
			.setParameter("nickname", nickname)
			.executeUpdate();
	}

	private void insertProjectGroup(Long id) {
		entityManager.createNativeQuery("""
			INSERT INTO project_group (
				id, project_name, project_title, status, created_at
			) VALUES (
				:id, 'TeamPo', 'TeamPo', 'ACTIVE', CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.executeUpdate();
	}

	private void insertProjectGroupMember(Long userId, Long projectGroupId) {
		entityManager.createNativeQuery("""
			INSERT INTO project_group_member (
				user_id, project_group_id, role, group_role, is_admin, created_at
			) VALUES (
				:userId, :projectGroupId, 'BACKEND', 'MEMBER', false, CURRENT_TIMESTAMP
			)
			""")
			.setParameter("userId", userId)
			.setParameter("projectGroupId", projectGroupId)
			.executeUpdate();
	}

	private void insertGithubAccount(Long id, Long userId, Long githubUserId, String githubUsername, Instant deletedAt) {
		entityManager.createNativeQuery("""
			INSERT INTO github_account (
				id, user_id, github_user_id, github_username, connected_at, created_at, deleted_at
			) VALUES (
				:id, :userId, :githubUserId, :githubUsername, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, :deletedAt
			)
			""")
			.setParameter("id", id)
			.setParameter("userId", userId)
			.setParameter("githubUserId", githubUserId)
			.setParameter("githubUsername", githubUsername)
			.setParameter("deletedAt", deletedAt)
			.executeUpdate();
	}

	private void insertContribution(
		Long id,
		Long projectGroupId,
		Long githubRepositoryId,
		Long githubPrId,
		Long authorGithubUserId,
		String authorGithubUsername,
		boolean merged,
		int linkedIssueCount,
		int additions,
		int deletions,
		int changedFiles
	) {
		entityManager.createNativeQuery("""
			INSERT INTO github_pull_request_contribution (
				id,
				project_group_id,
				github_repository_id,
				github_pr_id,
				pr_number,
				title,
				author_github_user_id,
				author_github_username,
				state,
				merged,
				merged_at,
				additions,
				deletions,
				changed_files,
				linked_issue_count,
				html_url,
				created_at,
				updated_at,
				synced_at
			) VALUES (
				:id,
				:projectGroupId,
				:githubRepositoryId,
				:githubPrId,
				:id,
				'PR title',
				:authorGithubUserId,
				:authorGithubUsername,
				'closed',
				:merged,
				CURRENT_TIMESTAMP,
				:additions,
				:deletions,
				:changedFiles,
				:linkedIssueCount,
				'https://github.com/student-team-org/backend/pull/1',
				CURRENT_TIMESTAMP,
				CURRENT_TIMESTAMP,
				CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.setParameter("projectGroupId", projectGroupId)
			.setParameter("githubRepositoryId", githubRepositoryId)
			.setParameter("githubPrId", githubPrId)
			.setParameter("authorGithubUserId", authorGithubUserId)
			.setParameter("authorGithubUsername", authorGithubUsername)
			.setParameter("merged", merged)
			.setParameter("additions", additions)
			.setParameter("deletions", deletions)
			.setParameter("changedFiles", changedFiles)
			.setParameter("linkedIssueCount", linkedIssueCount)
			.executeUpdate();
	}
}
