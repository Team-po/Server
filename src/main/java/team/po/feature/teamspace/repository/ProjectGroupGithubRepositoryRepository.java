package team.po.feature.teamspace.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.teamspace.domain.ProjectGroupGithubRepository;

public interface ProjectGroupGithubRepositoryRepository extends JpaRepository<ProjectGroupGithubRepository, Long> {
	long countByProjectGroup_IdAndDeletedAtIsNull(Long projectGroupId);

	List<ProjectGroupGithubRepository> findAllByProjectGroup_IdAndDeletedAtIsNull(Long projectGroupId);
}
