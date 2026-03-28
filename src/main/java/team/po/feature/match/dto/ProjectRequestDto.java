package team.po.feature.match.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import team.po.feature.match.domain.ProjectRequest;

public record ProjectRequestDto(
        @NotNull(message = "역할 선택은 필수입니다.")
        ProjectRequest.Role role,
        @NotBlank(message = "프로젝트 제목 입력은 필수입니다.")
        String projectTitle,
        String projectDescription,
        String projectMvp
) {
}