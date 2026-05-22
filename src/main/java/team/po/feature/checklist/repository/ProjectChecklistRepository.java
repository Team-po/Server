package team.po.feature.checklist.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.checklist.domain.ProjectChecklist;

public interface ProjectChecklistRepository extends JpaRepository<ProjectChecklist, Long> {

	@EntityGraph(attributePaths = {"assignee"})
	List<ProjectChecklist> findAllByProjectGroup_IdOrderByIdAsc(Long projectGroupId);

	@EntityGraph(attributePaths = {"assignee", "projectGroup"})
	Optional<ProjectChecklist> findByIdAndProjectGroup_Id(Long checklistId, Long projectGroupId);
}
