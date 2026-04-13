package team.po.feature.match.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.enums.Status;

public interface ProjectRequestRepository extends JpaRepository<ProjectRequest, Long> {
	public boolean existsByUserIdAndStatusIn(Long userId, List<Status> statuses);

	public Optional<ProjectRequest> findByUserIdAndStatusIn(Long userId, List<Status> statuses);
}
