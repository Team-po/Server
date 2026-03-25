package team.po.feature.match.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import team.po.feature.match.domain.ProjectRequest;

public interface ProjectRequestRepository extends JpaRepository<ProjectRequest, Long> {
}
