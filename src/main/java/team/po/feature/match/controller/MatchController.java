package team.po.feature.match.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import team.po.common.auth.LoginUser;
import team.po.feature.match.dto.MatchMemberResponse;
import team.po.feature.match.dto.MatchProjectResponse;
import team.po.feature.match.service.MatchService;
import team.po.feature.user.domain.Users;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/match")
public class MatchController {
	private final MatchService matchService;

	@Operation(summary = "매칭 멤버 조회 API")
	@GetMapping("/members")
	public ResponseEntity<MatchMemberResponse> getMatchMembers(
		@Parameter(hidden = true) @LoginUser Users user
	) {
		MatchMemberResponse response = matchService.getMatchMembers(user);
		return ResponseEntity.ok().body(response);
	}

	@Operation(summary = "매칭 프로젝트 정보 조회 API")
	@GetMapping("/project")
	public ResponseEntity<MatchProjectResponse> getMatchProject(
		@Parameter(hidden = true) @LoginUser Users user
	) {
		MatchProjectResponse response = matchService.getMatchProject(user);
		return ResponseEntity.ok().body(response);
	}

	@Operation(summary = "매칭 수락 API")
	@PostMapping("/{matchId}/accept")
	public ResponseEntity<Void> accept(
		@PathVariable Long matchId,
		@Parameter(hidden = true) @LoginUser Users user
	) {
		matchService.accept(matchId, user);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "매칭 거절 API")
	@PostMapping("/{matchId}/reject")
	public ResponseEntity<Void> reject(
		@PathVariable Long matchId,
		@Parameter(hidden = true) @LoginUser Users user
	) {
		matchService.reject(matchId, user);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "매칭 취소 API")
	@PostMapping(value = "/cancel")
	public ResponseEntity<Void> cancel(@Parameter(hidden = true) @LoginUser Users user) {
		matchService.cancel(user);
		return ResponseEntity.ok().build();
	}

}
