package team.po.feature.devguide.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import team.po.feature.devguide.dto.DevGuideContent;
import team.po.feature.devguide.service.DevGuideService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/team-space")
public class DevGuideController {
	private final DevGuideService devGuideService;

	@Operation(summary = "팀 스페이스 개발 가이드라인 조회 API")
	@GetMapping("/{projectGroupId}/dev-guide")
	public ResponseEntity<DevGuideContent> getDevGuide(
		@PathVariable Long projectGroupId
	) {
		return ResponseEntity.ok(
			devGuideService.getDevGuide(projectGroupId)
		);
	}
}
