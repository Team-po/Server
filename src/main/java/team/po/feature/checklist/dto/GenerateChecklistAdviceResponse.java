package team.po.feature.checklist.dto;

public record GenerateChecklistAdviceResponse(
	Long checklistId,
	ChecklistAiAdviceResponse aiAdvice
) {
}
