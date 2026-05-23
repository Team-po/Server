package team.po.feature.checklist.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import team.po.feature.checklist.domain.ProjectChecklistStatus;

public record UpdateProjectChecklistRequest(
	@NotBlank(message = "체크리스트 제목은 필수입니다.")
	@Size(max = 255, message = "체크리스트 제목은 255자 이하여야 합니다.")
	String title,
	@Size(max = 3000, message = "체크리스트 설명은 3000자 이하여야 합니다.")
	String description,
	@NotNull(message = "체크리스트 상태선택은 필수입니다.")
	ProjectChecklistStatus status,
	LocalDate dueDate,
	Long assigneeUserId
) {
}
