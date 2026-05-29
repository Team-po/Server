package team.po.feature.devguide.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.devguide.domain.DevGuideGeneration;

public interface DevGuideGenerationRepository extends JpaRepository<DevGuideGeneration, Long> {
	Optional<DevGuideGeneration> findByProjectGroup_Id(Long projectGroupId);
}