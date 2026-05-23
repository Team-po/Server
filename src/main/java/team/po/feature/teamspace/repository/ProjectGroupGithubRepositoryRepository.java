package team.po.feature.teamspace.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.teamspace.domain.ProjectGroupGithubRepository;

public interface ProjectGroupGithubRepositoryRepository extends JpaRepository<ProjectGroupGithubRepository, Long> {
	long countByProjectGroup_IdAndDeletedAtIsNull(Long projectGroupId);
}
