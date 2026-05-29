package team.po.feature.devguide.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.devguide.domain.DevGuide;

public interface DevGuideRepository extends JpaRepository<DevGuide, Long> {
	boolean existsByProjectGroup_Id(Long projectGroupId);

	Optional<DevGuide> findByProjectGroup_Id(Long projectGroupId);
}
