package team.po.feature.checklist.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import team.po.feature.checklist.dto.CreateProjectChecklistRequest;
import team.po.feature.checklist.dto.GenerateChecklistAdviceResponse;
import team.po.feature.checklist.dto.ProjectChecklistResponse;
import team.po.feature.checklist.dto.UpdateProjectChecklistRequest;
import team.po.feature.checklist.service.ProjectChecklistService;
import team.po.feature.user.domain.Users;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/project-groups/{projectGroupId}/checklists")
public class ProjectChecklistController {

	private final ProjectChecklistService projectChecklistService;

	@Operation(summary = "팀 스페이스 체크리스트 목록 조회 API")
	@GetMapping
	public ResponseEntity<List<ProjectChecklistResponse>> getProjectChecklists(
		@Parameter(hidden = true) @LoginUser Users requester,
		@PathVariable Long projectGroupId
	) {
		return ResponseEntity.ok(projectChecklistService.getProjectChecklists(projectGroupId, requester));
	}

	@Operation(summary = "팀 스페이스 체크리스트 생성 API")
	@PostMapping
	public ResponseEntity<ProjectChecklistResponse> createProjectChecklist(
		@Parameter(hidden = true) @LoginUser Users requester,
		@PathVariable Long projectGroupId,
		@Valid @RequestBody CreateProjectChecklistRequest request
	) {
		ProjectChecklistResponse response = projectChecklistService.createProjectChecklist(projectGroupId, requester, request);
		return ResponseEntity.status(HttpStatus.CREATED).body(response);
	}

	@Operation(summary = "팀 스페이스 체크리스트 수정 API")
	@PatchMapping("/{checklistId}")
	public ResponseEntity<ProjectChecklistResponse> updateProjectChecklist(
		@Parameter(hidden = true) @LoginUser Users requester,
		@PathVariable Long projectGroupId,
		@PathVariable Long checklistId,
		@Valid @RequestBody UpdateProjectChecklistRequest request
	) {
		return ResponseEntity.ok(
			projectChecklistService.updateProjectChecklist(projectGroupId, checklistId, requester, request)
		);
	}

	@Operation(summary = "팀 스페이스 체크리스트 삭제 API")
	@DeleteMapping("/{checklistId}")
	public ResponseEntity<Void> deleteProjectChecklist(
		@Parameter(hidden = true) @LoginUser Users requester,
		@PathVariable Long projectGroupId,
		@PathVariable Long checklistId
	) {
		projectChecklistService.deleteProjectChecklist(projectGroupId, checklistId, requester);
		return ResponseEntity.noContent().build();
	}

	@Operation(summary = "팀 스페이스 체크리스트 AI 조언 생성 API")
	@PostMapping("/{checklistId}/advice")
	public ResponseEntity<GenerateChecklistAdviceResponse> generateChecklistAdvice(
		@Parameter(hidden = true) @LoginUser Users requester,
		@PathVariable Long projectGroupId,
		@PathVariable Long checklistId
	) {
		return ResponseEntity.ok(
			projectChecklistService.generateChecklistAdvice(projectGroupId, checklistId, requester)
		);
	}
}
