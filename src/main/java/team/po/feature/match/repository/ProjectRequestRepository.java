package team.po.feature.match.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.enums.Role;
import team.po.feature.match.enums.Status;

public interface ProjectRequestRepository extends JpaRepository<ProjectRequest, Long> {
	public boolean existsByUserIdAndStatusIn(Long userId, List<Status> statuses);

	public Optional<ProjectRequest> findByUserIdAndStatusIn(Long userId, List<Status> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT pr FROM ProjectRequest pr
		JOIN FETCH pr.user
		WHERE pr.user.id = :userId
		  AND pr.status IN :statuses
		""")
	Optional<ProjectRequest> findByUserIdAndStatusInWithLock(
		@Param("userId") Long userId,
		@Param("statuses") List<Status> statuses
	);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT pr FROM ProjectRequest pr
		JOIN FETCH pr.user
		WHERE pr.id = :id
		""")
	Optional<ProjectRequest> findByIdWithLock(@Param("id") Long id);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		SELECT pr FROM ProjectRequest pr
		JOIN FETCH pr.user
		WHERE pr.id IN :ids
		ORDER BY pr.id ASC
		""")
	List<ProjectRequest> findAllByIdInWithLock(@Param("ids") List<Long> ids);

	// MATCHING
	// Host 대기자 조회: WAITING && isHost
	@Query("""
		SELECT pr FROM ProjectRequest pr
		JOIN FETCH pr.user u
		WHERE pr.status = team.po.feature.match.enums.Status.WAITING
		  AND u.deletedAt IS NULL
		  AND pr.projectTitle IS NOT NULL AND TRIM(pr.projectTitle) != ''
		  AND pr.projectDescription IS NOT NULL AND TRIM(pr.projectDescription) != ''
		  AND pr.projectMvp IS NOT NULL AND TRIM(pr.projectMvp) != ''
		""")
	List<ProjectRequest> findWaitingHosts(Pageable pageable);

	// Member 대기자 조회: WAITING && !isHost && 특정 Role
	@Query("""
		SELECT pr FROM ProjectRequest pr
		JOIN FETCH pr.user u
		WHERE pr.status = team.po.feature.match.enums.Status.WAITING
		  AND u.deletedAt IS NULL
		  AND pr.role = :role
		  AND (TRIM(COALESCE(pr.projectTitle, '')) = ''
		   		OR TRIM(COALESCE(pr.projectDescription, '')) = ''
		   		OR TRIM(COALESCE(pr.projectMvp, '')) = '')
		ORDER BY pr.createdAt ASC
		""")
	List<ProjectRequest> findWaitingMembersByRole(@Param("role") Role role);
}
