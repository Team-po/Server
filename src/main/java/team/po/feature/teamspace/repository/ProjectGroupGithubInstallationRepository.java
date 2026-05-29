package team.po.feature.teamspace.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.teamspace.domain.ProjectGroupGithubInstallation;

public interface ProjectGroupGithubInstallationRepository extends JpaRepository<ProjectGroupGithubInstallation, Long> {
	@EntityGraph(attributePaths = "githubInstallation")
	Optional<ProjectGroupGithubInstallation> findByProjectGroup_IdAndDeletedAtIsNull(Long projectGroupId);

	boolean existsByProjectGroup_IdAndDeletedAtIsNull(Long projectGroupId);
}
