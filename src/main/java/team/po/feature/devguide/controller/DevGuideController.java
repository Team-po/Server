package team.po.feature.devguide.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import team.po.common.auth.LoginUser;
import team.po.feature.devguide.dto.DevGuideQueryResponse;
import team.po.feature.devguide.dto.DevGuideRegenerateRequest;
import team.po.feature.devguide.dto.DevGuideRegenerateResponse;
import team.po.feature.devguide.service.DevGuideService;
import team.po.feature.user.domain.Users;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/team-space")
public class DevGuideController {
	private final DevGuideService devGuideService;

	@Operation(summary = "팀 스페이스 개발 가이드라인 조회 API")
	@GetMapping("/{projectGroupId}/dev-guide")
	public ResponseEntity<DevGuideQueryResponse> getDevGuide(
		@Parameter(hidden = true) @LoginUser Users user,
		@PathVariable Long projectGroupId
	) {
		DevGuideQueryResponse response = devGuideService.getDevGuide(projectGroupId, user.getId());

		return ResponseEntity.ok(response);
	}

	@Operation(summary = "팀 스페이스 개발 가이드라인 재생성 API")
	@PostMapping("/{projectGroupId}/dev-guide/regenerate")
	public ResponseEntity<DevGuideRegenerateResponse> regenerateDevGuide(
		@Parameter(hidden = true) @LoginUser Users user,
		@PathVariable Long projectGroupId,
		@Valid @RequestBody(required = false) DevGuideRegenerateRequest request
	) {
		String feedback = request != null ? request.feedback() : null;
		DevGuideRegenerateResponse response = devGuideService.regenerate(projectGroupId, user.getId(), feedback);

		return ResponseEntity.ok(response);
	}
}
