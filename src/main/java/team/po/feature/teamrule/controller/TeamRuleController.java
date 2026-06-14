package team.po.feature.teamrule.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import team.po.common.auth.LoginUser;
import team.po.feature.teamrule.dto.TeamRuleResponse;
import team.po.feature.teamrule.dto.UpdateTeamRuleRequest;
import team.po.feature.teamrule.service.TeamRuleService;
import team.po.feature.user.domain.Users;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/team-space/{projectGroupId}/team-rule")
public class TeamRuleController {

	private final TeamRuleService teamRuleService;

	@Operation(summary = "팀 룰 조회 API")
	@GetMapping
	public ResponseEntity<TeamRuleResponse> getTeamRule(
		@Parameter(hidden = true) @LoginUser Users user,
		@PathVariable Long projectGroupId
	) {
		TeamRuleResponse response = teamRuleService.getTeamRule(projectGroupId, user);
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "팀 룰 수정 API")
	@PutMapping
	public ResponseEntity<TeamRuleResponse> updateTeamRule(
		@Parameter(hidden = true) @LoginUser Users user,
		@PathVariable Long projectGroupId,
		@Valid @RequestBody UpdateTeamRuleRequest request
	) {
		TeamRuleResponse response = teamRuleService.updateTeamRule(projectGroupId, user, request);
		return ResponseEntity.ok(response);
	}
}
