package team.po.feature.checklist.dto;

import java.time.LocalDate;

import team.po.feature.checklist.domain.ProjectChecklistStatus;

public record ProjectChecklistResponse(
	Long id,
	String title,
	String description,
	ProjectChecklistStatus status,
	LocalDate dueDate,
	Long assigneeUserId,
	String assigneeNickname,
	ChecklistAiAdviceResponse aiAdvice
) {
}
