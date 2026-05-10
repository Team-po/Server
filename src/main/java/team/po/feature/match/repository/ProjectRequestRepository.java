package team.po.feature.match.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import team.po.feature.match.domain.ProjectRequest;
import team.po.feature.match.enums.Role;
import team.po.feature.match.enums.Status;

public interface ProjectRequestRepository extends JpaRepository<ProjectRequest, Long> {
	public boolean existsByUserIdAndStatusIn(Long userId, List<Status> statuses);

	public Optional<ProjectRequest> findByUserIdAndStatusIn(Long userId, List<Status> statuses);

	// MATCHING
	// Host 대기자 조회 (오래된 순): WAITING && isHost
	@Query("""
		SELECT pr FROM ProjectRequest pr
		JOIN FETCH pr.user
		WHERE pr.status = team.po.feature.match.enums.Status.WAITING
		  AND pr.projectTitle IS NOT NULL AND TRIM(pr.projectTitle) != ''
		  AND pr.projectDescription IS NOT NULL AND TRIM(pr.projectDescription) != ''
		  AND pr.projectMvp IS NOT NULL AND TRIM(pr.projectMvp) != ''
		""")
	List<ProjectRequest> findWaitingHosts(Pageable pageable);

	// Member 대기자 조회: WAITING && !isHost && 특정 Role
	@Query("""
		SELECT pr FROM ProjectRequest pr
		JOIN FETCH pr.user
		WHERE pr.status = team.po.feature.match.enums.Status.WAITING
		  AND pr.role = :role
		  AND (TRIM(COALESCE(pr.projectTitle, '')) = ''
		   		OR TRIM(COALESCE(pr.projectDescription, '')) = ''
		   		OR TRIM(COALESCE(pr.projectMvp, '')) = '')
		ORDER BY pr.createdAt ASC
		""")
	List<ProjectRequest> findWaitingMembersByRole(@Param("role") Role role);
}
