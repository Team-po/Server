package team.po.feature.devguide.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.devguide.domain.DevGuideGeneration;
import team.po.feature.devguide.domain.DevGuideStatus;

public interface DevGuideGenerationRepository extends JpaRepository<DevGuideGeneration, Long> {
	Optional<DevGuideGeneration> findByProjectGroup_Id(Long projectGroupId);

	List<DevGuideGeneration> findAllByStatusAndUpdatedAtBefore(DevGuideStatus status, LocalDateTime threshold);
}