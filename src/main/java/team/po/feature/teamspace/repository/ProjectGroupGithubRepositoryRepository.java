package team.po.feature.teamspace.repository;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectGroupGithubRepositoryRepository extends JpaRepository<ProjectGroupGithubRepositoryRepository, Long> {
	long countByProjectGroup_IdAndDeletedAtIsNull(Long projectGroupId);
}
