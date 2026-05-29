package team.po.feature.devguide.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import team.po.feature.devguide.domain.DevGuideStatus;

public record DevGuideQueryResponse(
	@JsonInclude(JsonInclude.Include.NON_NULL) DevGuideContent content,
	DevGuideStatus generationStatus,
	@JsonInclude(JsonInclude.Include.NON_NULL) Integer remainingRegenerationCount
) {
}