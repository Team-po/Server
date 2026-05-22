package team.po.feature.checklist.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectChecklistRequest(
	@NotBlank(message = "체크리스트 제목은 필수입니다.")
	@Size(max = 255, message = "체크리스트 제목은 255자 이하여야 합니다.")
	String title,
	@Size(max = 3000, message = "체크리스트 설명은 3000자 이하여야 합니다.")
	String description,
	LocalDate dueDate,
	Long assigneeUserId
) {
}
