package team.po.feature.devguide.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import team.po.feature.devguide.domain.DevGuideGenerationType;

public record DevGuideRegenerateResponse(
	DevGuideContent content,
	DevGuideGenerationType generationType,
	@JsonInclude(JsonInclude.Include.NON_NULL) Integer remainingRegenerationCount
) {
}