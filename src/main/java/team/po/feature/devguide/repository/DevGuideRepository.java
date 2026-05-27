package team.po.feature.devguide.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.devguide.domain.DevGuide;

public interface DevGuideRepository extends JpaRepository<DevGuide, Long> {
}
