package team.po.feature.devguide.dto;

import jakarta.validation.constraints.Size;

public record DevGuideRegenerateRequest(
	@Size(max = 500, message = "피드백은 500자 이하로 입력해주세요.")
	String feedback
) {
}