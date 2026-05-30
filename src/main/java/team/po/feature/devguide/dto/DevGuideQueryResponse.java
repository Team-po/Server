package team.po.feature.devguide.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import lombok.AllArgsConstructor;
import lombok.Getter;
import team.po.feature.devguide.domain.DevGuideStatus;

@Getter
@AllArgsConstructor
public class DevGuideQueryResponse {
	@JsonUnwrapped
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final DevGuideContent content;
	private final DevGuideStatus generationStatus;
	@JsonInclude(JsonInclude.Include.NON_NULL)
	private final Integer remainingRegenerationCount;
}