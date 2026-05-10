package team.po.feature.projectgroup.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import team.po.common.auth.LoginUser;
import team.po.feature.projectgroup.dto.GetMyProjectGroupResponse;
import team.po.feature.projectgroup.service.ProjectGroupService;
import team.po.feature.user.domain.Users;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/project-groups")
public class ProjectGroupController {

	private final ProjectGroupService projectGroupService;

	@Operation(summary = "내 팀 스페이스 조회 API")
	@GetMapping("/me")
	public ResponseEntity<GetMyProjectGroupResponse> getMyProjectGroup(
		@Parameter(hidden = true) @LoginUser Users requester
	) {
		GetMyProjectGroupResponse response = projectGroupService.getMyProjectGroup(requester);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "팀 스페이스 관리자 권한 부여 API")
	@PatchMapping("/{projectGroupId}/admins/{targetUserId}")
	public ResponseEntity<Void> grantAdminPermission(
		@Parameter(hidden = true) @LoginUser Users requester,
		@PathVariable Long projectGroupId,
		@PathVariable Long targetUserId
	) {
		projectGroupService.grantAdminPermission(projectGroupId, requester.getId(), targetUserId);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "팀 스페이스 관리자 권한 회수 API")
	@DeleteMapping("/{projectGroupId}/admins/{targetUserId}")
	public ResponseEntity<Void> revokeAdminPermission(
		@Parameter(hidden = true) @LoginUser Users requester,
		@PathVariable Long projectGroupId,
		@PathVariable Long targetUserId
	) {
		projectGroupService.revokeAdminPermission(projectGroupId, requester.getId(), targetUserId);
		return ResponseEntity.ok().build();
	}
}
