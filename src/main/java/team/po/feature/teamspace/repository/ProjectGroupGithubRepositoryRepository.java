package team.po.feature.teamspace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import team.po.feature.teamspace.domain.ProjectGroupGithubRepository;
import team.po.feature.teamspace.dto.GithubPullRequestSyncContext;

public interface ProjectGroupGithubRepositoryRepository extends JpaRepository<ProjectGroupGithubRepository, Long> {
	long countByProjectGroup_IdAndDeletedAtIsNull(Long projectGroupId);

	List<ProjectGroupGithubRepository> findAllByProjectGroup_IdAndDeletedAtIsNull(Long projectGroupId);

	Optional<ProjectGroupGithubRepository> findByProjectGroup_IdAndGithubRepositoryIdAndDeletedAtIsNull(
		Long projectGroupId,
		Long githubRepositoryId
	);

	@Query("""
		SELECT new team.po.feature.teamspace.dto.GithubPullRequestSyncContext(
			installation.installationId,
			repository.owner,
			repository.repoName
		)
		FROM ProjectGroupGithubRepository repository
		JOIN repository.githubInstallation installation
		WHERE repository.projectGroup.id = :projectGroupId
			AND repository.githubRepositoryId = :githubRepositoryId
			AND repository.deletedAt IS NULL
		""")
	Optional<GithubPullRequestSyncContext> findGithubPullRequestSyncContext(
		@Param("projectGroupId") Long projectGroupId,
		@Param("githubRepositoryId") Long githubRepositoryId
	);
}
