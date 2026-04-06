package team.po.feature.match.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.*;
import team.po.common.auth.LoginUser;
import team.po.common.auth.LoginUserInfo;
import team.po.exception.ErrorCodeConstants;
import team.po.exception.InvalidFieldException;
import team.po.feature.match.dto.ProjectRequestDto;
import team.po.feature.match.dto.ProjectRequestStatusResponse;
import team.po.feature.match.service.ProjectRequestService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/match")
public class ProjectRequestController {
    private final ProjectRequestService projectRequestService;

    @Operation(summary = "매칭 요청 API")
    @PostMapping(value = "/request")
    public ResponseEntity<Void> createProjectRequest(@LoginUser LoginUserInfo loginUser, @Valid @RequestBody ProjectRequestDto request, Errors errors){ // @Valid: 두 번째 파라미터 에러 받아서 처리
        if (errors.hasErrors()) {
            throw new InvalidFieldException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCodeConstants.INVALID_INPUT_FIELD,
                    "입력값이 올바르지 않습니다.",
                    errors
            );
        }
        projectRequestService.createProjectRequest(loginUser, request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "매칭 취소 API")
    @PatchMapping(value = "/cancel")
    public ResponseEntity<Void> cancelProjectRequest(@LoginUser LoginUserInfo loginUser){
        projectRequestService.cancelProjectRequest(loginUser);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "매칭 상태 조회 API")
    @GetMapping(value = "/status")
    public ResponseEntity<ProjectRequestStatusResponse> getProjectRequestStatus(@LoginUser LoginUserInfo loginUser) {
        ProjectRequestStatusResponse response = projectRequestService.getProjectRequestStatus(loginUser);
        return ResponseEntity.ok().body(response);
    }
}
