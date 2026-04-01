package team.po.feature.match.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.enums.Status;

import java.util.List;
import java.util.Optional;

public interface ProjectRequestRepository extends JpaRepository<ProjectRequest, Long> {
    public Optional<ProjectRequest> findByUserIdAndStatusIn(Long userId, List<Status> statuses);
}
