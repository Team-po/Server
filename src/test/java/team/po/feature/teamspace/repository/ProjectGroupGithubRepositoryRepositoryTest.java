package team.po.feature.teamspace.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import team.po.feature.teamspace.dto.GithubPullRequestSyncContext;

@DataJpaTest
@ActiveProfiles("h2")
class ProjectGroupGithubRepositoryRepositoryTest {

	@Autowired
	private ProjectGroupGithubRepositoryRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void findGithubPullRequestSyncContext_returnsInstallationAndRepositoryFields() {
		insertProjectGroup(10L);
		insertGithubInstallation(5L, 12345L);
		insertGithubRepository(1L, 10L, 5L, 100L, "student-team-org", "backend", null);
		entityManager.flush();
		entityManager.clear();

		GithubPullRequestSyncContext context = repository.findGithubPullRequestSyncContext(10L, 100L)
			.orElseThrow();

		assertThat(context.installationId()).isEqualTo(12345L);
		assertThat(context.owner()).isEqualTo("student-team-org");
		assertThat(context.repoName()).isEqualTo("backend");
	}

	@Test
	void findGithubPullRequestSyncContext_returnsEmpty_whenRepositoryIsDeleted() {
		insertProjectGroup(10L);
		insertGithubInstallation(5L, 12345L);
		insertGithubRepository(
			1L,
			10L,
			5L,
			100L,
			"student-team-org",
			"backend",
			Instant.parse("2026-05-01T00:00:00Z")
		);
		entityManager.flush();
		entityManager.clear();

		assertThat(repository.findGithubPullRequestSyncContext(10L, 100L)).isEmpty();
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

	private void insertGithubInstallation(Long id, Long installationId) {
		entityManager.createNativeQuery("""
			INSERT INTO github_installation (
				id,
				installation_id,
				account_id,
				account_login,
				account_type,
				created_at,
				updated_at
			) VALUES (
				:id,
				:installationId,
				98765,
				'student-team-org',
				'Organization',
				CURRENT_TIMESTAMP,
				CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.setParameter("installationId", installationId)
			.executeUpdate();
	}

	private void insertGithubRepository(
		Long id,
		Long projectGroupId,
		Long githubInstallationId,
		Long githubRepositoryId,
		String owner,
		String repoName,
		Instant deletedAt
	) {
		entityManager.createNativeQuery("""
			INSERT INTO project_group_github_repository (
				id,
				project_group_id,
				github_installation_id,
				github_repository_id,
				owner,
				repo_name,
				full_name,
				default_branch,
				is_private,
				created_at,
				deleted_at
			) VALUES (
				:id,
				:projectGroupId,
				:githubInstallationId,
				:githubRepositoryId,
				:owner,
				:repoName,
				:fullName,
				'main',
				true,
				CURRENT_TIMESTAMP,
				:deletedAt
			)
			""")
			.setParameter("id", id)
			.setParameter("projectGroupId", projectGroupId)
			.setParameter("githubInstallationId", githubInstallationId)
			.setParameter("githubRepositoryId", githubRepositoryId)
			.setParameter("owner", owner)
			.setParameter("repoName", repoName)
			.setParameter("fullName", owner + "/" + repoName)
			.setParameter("deletedAt", deletedAt)
			.executeUpdate();
	}
}
