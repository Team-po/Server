package team.po.feature.projectgroup.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import team.po.feature.projectgroup.domain.GroupRole;
import team.po.feature.projectgroup.domain.ProjectGroupMember;

public interface ProjectGroupMemberRepository extends JpaRepository<ProjectGroupMember, Long> {
	boolean existsByUser_IdIn(List<Long> userIds);

	Optional<ProjectGroupMember> findByProjectGroup_IdAndGroupRole(Long projectGroupId, GroupRole groupRole);	//역할조회용

	List<ProjectGroupMember> findAllByProjectGroup_Id(Long projectGroupId);	//멤버 전체조회
}
