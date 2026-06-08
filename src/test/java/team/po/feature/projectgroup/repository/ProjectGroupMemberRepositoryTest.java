package team.po.feature.projectgroup.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import team.po.feature.projectgroup.domain.ProjectGroupMember;
import team.po.feature.projectgroup.domain.ProjectGroupStatus;

@DataJpaTest
@ActiveProfiles("h2")
class ProjectGroupMemberRepositoryTest {

	@Autowired
	private ProjectGroupMemberRepository repository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void findWeeklyGithubSummaryTargetMembers_returnsOnlyEligibleActiveMembers() {
		insertUser(1L, "target@example.com", "target");
		insertUser(2L, "no-github-account@example.com", "no-github-account");
		insertUser(3L, "finished-group-member@example.com", "finished-group-member");
		insertProjectGroup(10L, ProjectGroupStatus.ACTIVE);
		insertProjectGroup(20L, ProjectGroupStatus.FINISHED);
		insertProjectGroupMember(100L, 1L, 10L);
		insertProjectGroupMember(200L, 2L, 10L);
		insertProjectGroupMember(300L, 3L, 20L);
		insertGithubInstallation(1L, 12345L);
		insertGithubInstallation(2L, 67890L);
		insertProjectGroupGithubInstallation(1L, 10L, 1L, 1L, null);
		insertProjectGroupGithubInstallation(2L, 20L, 2L, 3L, null);
		insertProjectGroupGithubRepository(1L, 10L, 1L, 1001L, null);
		insertProjectGroupGithubRepository(2L, 20L, 2L, 2001L, null);
		insertGithubAccount(1L, 1L, 501L, null);
		insertGithubAccount(2L, 3L, 503L, null);
		entityManager.flush();
		entityManager.clear();

		List<ProjectGroupMember> members = repository.findWeeklyGithubSummaryTargetMembers(ProjectGroupStatus.ACTIVE);

		assertThat(members)
			.extracting(ProjectGroupMember::getId)
			.containsExactly(100L);
		assertThat(members.get(0).getUser().getId()).isEqualTo(1L);
		assertThat(members.get(0).getProjectGroup().getId()).isEqualTo(10L);
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

	private void insertProjectGroup(Long id, ProjectGroupStatus status) {
		entityManager.createNativeQuery("""
			INSERT INTO project_group (
				id, project_name, project_title, status, created_at
			) VALUES (
				:id, 'TeamPo', 'TeamPo', :status, CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.setParameter("status", status.name())
			.executeUpdate();
	}

	private void insertProjectGroupMember(Long id, Long userId, Long projectGroupId) {
		entityManager.createNativeQuery("""
			INSERT INTO project_group_member (
				id, user_id, project_group_id, role, group_role, is_admin, is_finish_agreed, created_at
			) VALUES (
				:id, :userId, :projectGroupId, 'BACKEND', 'MEMBER', false, false, CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.setParameter("userId", userId)
			.setParameter("projectGroupId", projectGroupId)
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
				:accountId,
				'student-team-org',
				'Organization',
				CURRENT_TIMESTAMP,
				CURRENT_TIMESTAMP
			)
			""")
			.setParameter("id", id)
			.setParameter("installationId", installationId)
			.setParameter("accountId", 9000L + id)
			.executeUpdate();
	}

	private void insertProjectGroupGithubInstallation(
		Long id,
		Long projectGroupId,
		Long githubInstallationId,
		Long connectedByUserId,
		Instant deletedAt
	) {
		entityManager.createNativeQuery("""
			INSERT INTO project_group_github_installation (
				id,
				project_group_id,
				github_installation_id,
				connected_by,
				created_at,
				deleted_at
			) VALUES (
				:id,
				:projectGroupId,
				:githubInstallationId,
				:connectedByUserId,
				CURRENT_TIMESTAMP,
				:deletedAt
			)
			""")
			.setParameter("id", id)
			.setParameter("projectGroupId", projectGroupId)
			.setParameter("githubInstallationId", githubInstallationId)
			.setParameter("connectedByUserId", connectedByUserId)
			.setParameter("deletedAt", deletedAt)
			.executeUpdate();
	}

	private void insertProjectGroupGithubRepository(
		Long id,
		Long projectGroupId,
		Long githubInstallationId,
		Long githubRepositoryId,
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
				'student-team-org',
				'backend',
				'student-team-org/backend',
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
			.setParameter("deletedAt", deletedAt)
			.executeUpdate();
	}

	private void insertGithubAccount(Long id, Long userId, Long githubUserId, Instant deletedAt) {
		entityManager.createNativeQuery("""
			INSERT INTO github_account (
				id,
				user_id,
				github_user_id,
				github_username,
				access_token_ciphertext,
				token_type,
				github_scopes,
				connected_at,
				created_at,
				deleted_at
			) VALUES (
				:id,
				:userId,
				:githubUserId,
				:githubUsername,
				'access-token-ciphertext',
				'bearer',
				'repo',
				CURRENT_TIMESTAMP,
				CURRENT_TIMESTAMP,
				:deletedAt
			)
			""")
			.setParameter("id", id)
			.setParameter("userId", userId)
			.setParameter("githubUserId", githubUserId)
			.setParameter("githubUsername", "github-user-" + userId)
			.setParameter("deletedAt", deletedAt)
			.executeUpdate();
	}
}
