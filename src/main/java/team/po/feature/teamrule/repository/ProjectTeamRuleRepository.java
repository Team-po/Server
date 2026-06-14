package team.po.feature.teamrule.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.teamrule.domain.ProjectTeamRule;

public interface ProjectTeamRuleRepository extends JpaRepository<ProjectTeamRule, Long> {
	Optional<ProjectTeamRule> findByProjectGroup_Id(Long projectGroupId);
}
