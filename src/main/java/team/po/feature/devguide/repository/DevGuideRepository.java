package team.po.feature.devguide.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import team.po.feature.devguide.domain.DevGuide;
import team.po.feature.devguide.domain.DevGuideGenerationType;

public interface DevGuideRepository extends JpaRepository<DevGuide, Long> {
	boolean existsByProjectGroup_IdAndDeletedAtIsNull(Long projectGroupId);

	boolean existsByProjectGroup_IdAndIsConfirmedTrue(Long projectGroupId);

	Optional<DevGuide> findByProjectGroup_IdAndIsConfirmedTrue(Long projectGroupId);

	Optional<DevGuide> findByIdAndProjectGroup_IdAndDeletedAtIsNull(Long id, Long projectGroupId);

	List<DevGuide> findAllByProjectGroup_IdAndIsConfirmedTrueAndDeletedAtIsNull(Long projectGroupId);

	@Query("SELECT COALESCE(MAX(d.versionNo), 0) FROM DevGuide d WHERE d.projectGroup.id = :projectGroupId")
	int findMaxVersionNoByProjectGroupId(@Param("projectGroupId") Long projectGroupId);

	// 재생성 횟수 제한 체크용 (RECOVERY는 횟수 미차감, MANUAL만 차감)
	int countByProjectGroup_IdAndGenerationType(Long projectGroupId, DevGuideGenerationType generationType);
}
