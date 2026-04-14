package team.po.feature.projectgroup.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.projectgroup.domain.ProjectGroup;

public interface ProjectGroupRepository extends JpaRepository<ProjectGroup, Long> {
}
