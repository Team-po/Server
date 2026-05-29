package team.po.feature.teamspace.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.teamspace.domain.ProjectGroupGithubRepository;

public interface ProjectGroupGithubRepositoryRepository extends JpaRepository<ProjectGroupGithubRepository, Long> {
	long countByProjectGroup_IdAndDeletedAtIsNull(Long projectGroupId);

	List<ProjectGroupGithubRepository> findAllByProjectGroup_IdAndDeletedAtIsNull(Long projectGroupId);

	Optional<ProjectGroupGithubRepository> findByProjectGroup_IdAndGithubRepositoryIdAndDeletedAtIsNull(
		Long projectGroupId,
		Long githubRepositoryId
	);
}
