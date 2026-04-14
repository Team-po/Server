package team.po.feature.projectgroup.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.po.common.auth.LoginUser;
import team.po.exception.ErrorCodeConstants;
import team.po.exception.InvalidFieldException;
import team.po.feature.projectgroup.dto.CreateProjectGroupRequest;
import team.po.feature.projectgroup.dto.CreateProjectGroupResponse;
import team.po.feature.projectgroup.service.ProjectGroupService;
import team.po.feature.user.domain.Users;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/project-groups")
public class ProjectGroupController {

	private final ProjectGroupService projectGroupService;

	@Operation(summary = "팀 스페이스 생성 API")
	@PostMapping
	public ResponseEntity<CreateProjectGroupResponse> createProjectGroup(
		@Parameter(hidden = true) @LoginUser Users requester,
		@Valid @RequestBody CreateProjectGroupRequest request,
		Errors errors
	) {
		if (errors.hasErrors()) {
			throw new InvalidFieldException(
				HttpStatus.BAD_REQUEST,
				ErrorCodeConstants.INVALID_INPUT_FIELD,
				"입력값이 올바르지 않습니다.",
				errors
			);
		}

		CreateProjectGroupResponse response = projectGroupService.createProjectGroup(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
