package team.po.feature.devguide.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import team.po.feature.devguide.domain.DevGuide;

public interface DevGuideRepository extends JpaRepository<DevGuide, Long> {
	boolean existsByProjectGroup_IdAndIsConfirmedTrue(Long projectGroupId);

	Optional<DevGuide> findByProjectGroup_IdAndIsConfirmedTrue(Long projectGroupId);

	@Query("SELECT COALESCE(MAX(d.versionNo), 0) FROM DevGuide d WHERE d.projectGroup.id = :projectGroupId")
	int findMaxVersionNoByProjectGroupId(@Param("projectGroupId") Long projectGroupId);

	// 재생성 횟수 제한 체크용 (version 1이 최초 생성이므로 재생성 횟수 = count - 1)
	int countByProjectGroup_Id(Long projectGroupId);
}