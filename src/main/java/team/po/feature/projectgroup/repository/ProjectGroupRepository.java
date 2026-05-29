package team.po.feature.projectgroup.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import team.po.feature.projectgroup.domain.ProjectGroup;

public interface ProjectGroupRepository extends JpaRepository<ProjectGroup, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select pg from ProjectGroup pg where pg.id = :id")
	Optional<ProjectGroup> findByIdForUpdate(@Param("id") Long id);
}
