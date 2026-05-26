package team.po.feature.teamspace.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
import team.po.feature.teamspace.dto.CompleteGithubAppInstallationRequest;
import team.po.feature.teamspace.dto.CreateGithubAppInstallationUrlResponse;
import team.po.feature.teamspace.dto.GetGithubInstallationStatusResponse;
import team.po.feature.teamspace.dto.GetAvailiableGithubRepositoryList;
import team.po.feature.teamspace.service.TeamspaceService;
import team.po.feature.user.domain.Users;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/team-space")
public class TeamspaceController {

	private final TeamspaceService teamspaceService;

	@Operation(summary = "팀 스페이스 Github App 설치 상태 조회 API")
	@GetMapping("/{projectGroupId}/github/status")
	public ResponseEntity<GetGithubInstallationStatusResponse> getGithubInstallationStatus(
		@Parameter(hidden = true) @LoginUser Users user,
		@PathVariable Long projectGroupId) {
		GetGithubInstallationStatusResponse response = teamspaceService.getGithubInstallationStatus(
			projectGroupId,
			user.getId()
		);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Github App 설치 URL 생성 API")
	@PostMapping("/{projectGroupId}/github/install-url")
	public ResponseEntity<CreateGithubAppInstallationUrlResponse> createGithubAppInstallationUrl(
		@Parameter(hidden = true) @LoginUser Users user,
		@PathVariable Long projectGroupId) {
		CreateGithubAppInstallationUrlResponse response = teamspaceService.createGithubAppInstallationUrl(user, projectGroupId);

		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Github 설치 완료 API")
	@PostMapping("/{projectGroupId}/github/installations/complete")
	public ResponseEntity<Void> completeGithubAppInstallation(
		@Parameter(hidden = true) @LoginUser Users user,
		@Valid @RequestBody CompleteGithubAppInstallationRequest request,
		@PathVariable Long projectGroupId
	) {

		teamspaceService.completeGithubAppInstallation(request, projectGroupId, user.getId());
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "Github App 접근 가능 Repository 목록 조회")
	@GetMapping("/{projectGroupId}/github/repositories")
	public ResponseEntity<GetAvailiableGithubRepositoryList> getAvailiableGithubRepositoryList(
		@Parameter(hidden = true) @LoginUser Users user,
		@PathVariable Long projectGroupId
	) {
		GetAvailiableGithubRepositoryList response = teamspaceService.getAvailiableGithubRepositoryList(user, projectGroupId);

		return ResponseEntity.ok(response);
	}


}
