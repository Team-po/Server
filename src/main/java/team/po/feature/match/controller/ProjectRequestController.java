package team.po.feature.match.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.po.common.auth.LoginUser;
import team.po.feature.match.dto.ProjectRequestDto;
import team.po.feature.match.dto.ProjectRequestStatusResponse;
import team.po.feature.match.service.ProjectRequestService;
import team.po.feature.user.domain.Users;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/match")
public class ProjectRequestController {
	private final ProjectRequestService projectRequestService;

	@Operation(summary = "매칭 요청 API")
	@PostMapping(value = "/request")
	public ResponseEntity<Void> createProjectRequest(@LoginUser Users user,
		@Valid @RequestBody ProjectRequestDto request) {
		projectRequestService.createProjectRequest(user, request);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "매칭 취소 API")
	@PatchMapping(value = "/cancel")
	public ResponseEntity<Void> cancelProjectRequest(@LoginUser Users user) {
		projectRequestService.cancelProjectRequest(user);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "매칭 상태 조회 API")
	@GetMapping(value = "/status")
	public ResponseEntity<ProjectRequestStatusResponse> getProjectRequestStatus(@LoginUser Users user) {
		ProjectRequestStatusResponse response = projectRequestService.getProjectRequestStatus(user);
		return ResponseEntity.ok().body(response);
	}
}
