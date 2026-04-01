package team.po.feature.match.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import team.po.common.auth.LoginUser;
import team.po.common.auth.LoginUserInfo;
import team.po.feature.match.dto.ProjectRequestDto;
import team.po.feature.match.service.ProjectRequestService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/match")
public class ProjectRequestController {
    private final ProjectRequestService projectRequestService;

    @Operation(summary = "매칭 요청 API")
    @PostMapping(value = "/request")
    public ResponseEntity<Void> createProjectRequest(@Valid @RequestBody ProjectRequestDto dto){ // @Valid: 두 번째 파라미터 에러 받아서 처리

        projectRequestService.createProjectRequest(dto);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "매칭 취소 API")
    @PatchMapping(value = "/cancel")
    public ResponseEntity<Void> cancelProjectRequest(@LoginUser LoginUserInfo loginUser){
        projectRequestService.cancelProjectRequest(loginUser);
        return ResponseEntity.ok().build();
    }
}
